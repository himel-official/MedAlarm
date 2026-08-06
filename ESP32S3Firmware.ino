/*
 *Author: Himel Mahmud
 * final version of ESP32 Firmware
 * ============================================================================
 *  ESPMEDALARM  -  ESP32-S3 (N16R8) firmware
 * ============================================================================
 *
 *  Matches the companion Android app (ESPMEDALARM). The app always talks to
 *  a FIXED IP:  http://192.168.4.1   and POSTs JSON to  /sync  in this shape:
 *
 *  {
 *    "currentTime": 1735689600000,        // phone epoch millis (UTC)
 *    "medicines": [
 *      {
 *        "id": 1,
 *        "name": "Paracetamol",
 *        "box": 1,                        // 1-4
 *        "duration": 7,                   // days the schedule is active
 *        "startDate": 1735689600000,      // epoch millis (UTC)
 *        "times": ["08:00","20:00"]       // local wall-clock HH:MM
 *      }
 *    ]
 *  }
 *
 *  REQUIRED LIBRARIES (Library Manager):
 *    - ArduinoJson        (by Benoit Blanchon, v7.x)
 *    - U8g2                (by oliver)
 *  (Servos are driven with the ESP32 core's native ledcAttach/ledcWrite LEDC
 *   API, one independent channel per pin, so no ESP32Servo library needed.)
 *
 *  BOARD:  ESP32S3 Dev Module (N16R8: 16MB flash / 8MB OCTAL PSRAM)
 *  IMPORTANT: N16R8 uses GPIO 33-37 internally for the octal PSRAM.
 *  Do NOT use GPIO 33-37 for anything. Also avoid 0,19,20,26-32,43,44,45,46
 *  (strap / USB / UART0 pins). The pin map below only uses safe GPIOs.
 *
 *  HOW IT WORKS
 *  -------------
 *  - No RTC chip: time is kept with ESP32's internal system clock. It is set
 *    from the phone every time you sync, and is LOST on power loss / reset,
 *    so you'll need to re-sync (hold button 5s) after unplugging the box.
 *  - First boot (or before any sync): OLED shows "press button for 5 sec to
 *    sync". Holding the button 5s starts a WiFi Access Point (192.168.4.1)
 *    that stays up for up to 4 minutes. Open the app, connect your phone's
 *    WiFi to the ESP's hotspot, then tap Sync in the app.
 *  - After a successful sync the box knows the time + medicine schedule and
 *    goes to the idle clock screen.
 *  - At an alarm: OLED shows the medicine name, buzzer beeps, the box's LED
 *    turns on and its servo opens the lid.
 *      - short press (<3s)  -> taken. Alarm stops immediately, box auto
 *                              closes 60s later.
 *      - no press / long press ignored -> after 5 minutes the alarm stops,
 *                              medicine is marked "skipped", box closes.
 *  - Idle screen: digital clock + list of medicines skipped today. The
 *    skipped list clears automatically at midnight.
 * ============================================================================
 */

#include <WiFi.h>
#include <WebServer.h>
#include <ArduinoJson.h>
#include <U8g2lib.h>
#include <Wire.h>
#include <time.h>

// ---------------------------------------------------------------------------
// USER-EDITABLE SETTINGS
// ---------------------------------------------------------------------------

// Your local timezone offset from UTC, in seconds. The phone sends UTC epoch
// millis, so this is what converts that into the wall-clock time your
// medicine times ("08:00" etc.) are meant to match.
// Default below = UTC+6 (Bangladesh). Change if you're elsewhere.
#define TIMEZONE_OFFSET_SEC (6 * 3600)

// Access point shown while syncing
#define AP_SSID "MedAlarm-Setup"
#define AP_PASSWORD "20245103282"
#define AP_TIMEOUT_MS (4UL * 60UL * 1000UL)  // hotspot stays on max 4 minutes

// Alarm behaviour
#define ALARM_DURATION_MS (5UL * 60UL * 1000UL)  // buzzer/lid open window if not acknowledged
#define TAKEN_CLOSE_DELAY_MS (60UL * 1000UL)     // box closes 60s after "taken"
#define SHORT_PRESS_MAX_MS 3000UL                // < 3s counts as "taken"
#define LONG_PRESS_SYNC_MS 5000UL                // hold 5s to enter sync mode
#define BUZZER_BEEP_ON_MS 400
#define BUZZER_BEEP_OFF_MS 400

// Servo angles
#define SERVO_OPEN_ANGLE 180
#define SERVO_CLOSED_ANGLE 0

// ---------------------------------------------------------------------------
// PIN MAP
// ---------------------------------------------------------------------------
#define PIN_I2C_SDA 8
#define PIN_I2C_SCL 9

#define PIN_BUTTON 4
#define PIN_BUZZER 5

#define PIN_LED_BOX1 15
#define PIN_LED_BOX2 16
#define PIN_LED_BOX3 17
#define PIN_LED_BOX4 18

#define PIN_SERVO_BOX1 10
#define PIN_SERVO_BOX2 13
#define PIN_SERVO_BOX3 12
#define PIN_SERVO_BOX4 14

const int LED_PINS[4] = { PIN_LED_BOX1, PIN_LED_BOX2, PIN_LED_BOX3, PIN_LED_BOX4 };
const int SERVO_PINS[4] = { PIN_SERVO_BOX1, PIN_SERVO_BOX2, PIN_SERVO_BOX3, PIN_SERVO_BOX4 };

// ---- SERVO PWM CONFIG (native LEDC, no ESP32Servo library) ----
// Each servo pin gets its own LEDC channel via ledcAttach(), so boxes move
// fully independently (avoids the ESP32Servo shared-timer pairing where
// box1/box3 and box2/box4 would otherwise move together).
const int SERVO_FREQ_HZ = 50;       // standard servo frequency
const int SERVO_RES_BITS = 14;      // duty resolution (14-bit gives fine control at 50Hz)
const int SERVO_MIN_US = 500;       // pulse width at 0 degrees
const int SERVO_MAX_US = 2400;      // pulse width at 180 degrees
const uint32_t SERVO_PERIOD_US = 1000000UL / SERVO_FREQ_HZ;  // 20000us

void setServoAngle(int pin, int angleDeg) {
  angleDeg = constrain(angleDeg, 0, 180);
  int pulseUs = map(angleDeg, 0, 180, SERVO_MIN_US, SERVO_MAX_US);
  uint32_t maxDuty = (1UL << SERVO_RES_BITS) - 1;
  uint32_t duty = (uint32_t)(((uint64_t)pulseUs * maxDuty) / SERVO_PERIOD_US);
  ledcWrite(pin, duty);
}

// ---------------------------------------------------------------------------
// DATA MODEL
// ---------------------------------------------------------------------------
#define MAX_MEDS 40
#define MAX_TIMES_PER_MED 6
#define MAX_QUEUE 16
#define MAX_SKIPPED 10

struct Medicine {
  int id;
  char name[32];
  int box;              // 1-4
  int duration;         // days
  long long startDate;  // epoch millis (UTC)
  int timesCount;
  char times[MAX_TIMES_PER_MED][6];  // "HH:MM"
  bool triggeredToday[MAX_TIMES_PER_MED];
};

Medicine medicines[MAX_MEDS];
int medicineCount = 0;

struct QueueItem {
  int medIndex;
  int timeIndex;
};
QueueItem alarmQueue[MAX_QUEUE];
int qHead = 0, qTail = 0, qCount = 0;

struct SkippedEntry {
  char name[32];
  char time[6];
};
SkippedEntry skippedList[MAX_SKIPPED];
int skippedCount = 0;

int lastSeenDay = -1;  // day-of-year, used to detect midnight rollover

// ---------------------------------------------------------------------------
// STATE MACHINE
// ---------------------------------------------------------------------------
enum SystemState { STATE_WAIT_SYNC,
                   STATE_AP_MODE,
                   STATE_IDLE,
                   STATE_ALARM,
                   STATE_BOX_CLOSING };
SystemState systemState = STATE_WAIT_SYNC;

bool timeSynced = false;

// active alarm bookkeeping
int activeMedIndex = -1;
int activeTimeIndex = -1;
unsigned long alarmStartMillis = 0;
unsigned long boxCloseAtMillis = 0;
int activeBox = -1;
bool buzzerOn = false;
unsigned long buzzerToggleMillis = 0;

// AP mode bookkeeping
unsigned long apStartMillis = 0;
bool apSyncReceived = false;
unsigned long apSyncReceivedMillis = 0;

// button bookkeeping
bool buttonPressed = false;
unsigned long buttonPressStart = 0;
bool longPressTriggered = false;
unsigned long lastButtonChangeMillis = 0;
bool lastRawButtonState = HIGH;

// display throttle
unsigned long lastDisplayMillis = 0;

// ---------------------------------------------------------------------------
// OBJECTS
// ---------------------------------------------------------------------------
U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, /* reset=*/U8X8_PIN_NONE);
WebServer server(80);

// ---------------------------------------------------------------------------
// FORWARD DECLARATIONS
// ---------------------------------------------------------------------------
void startApMode();
void stopApMode();
void handleSync();
void handleRoot();
void handleButton();
void checkDailyReset();
void checkSchedule();
void startNextAlarm();
void markTaken();
void markSkipped();
void closeBox();
void updateBuzzer();
void updateDisplay();
void drawWaitSync();
void drawApMode();
void drawIdle();
void drawAlarm();
bool queuePush(int medIndex, int timeIndex);
bool queuePop(QueueItem &out);
void getLocalTm(struct tm &out);

// ---------------------------------------------------------------------------
// SETUP
// ---------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);

  pinMode(PIN_BUTTON, INPUT_PULLUP);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  for (int i = 0; i < 4; i++) {
    pinMode(LED_PINS[i], OUTPUT);
    digitalWrite(LED_PINS[i], LOW);
  }

  // Servos are externally powered - only the signal line comes from the ESP32
  for (int i = 0; i < 4; i++) {
    // ledcAttach(pin, freq, resolution_bits) — core auto-assigns channel/timer per pin
    ledcAttach(SERVO_PINS[i], SERVO_FREQ_HZ, SERVO_RES_BITS);
    setServoAngle(SERVO_PINS[i], SERVO_CLOSED_ANGLE);
  }

  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  u8g2.begin();
  u8g2.setBusClock(400000);

  systemState = STATE_WAIT_SYNC;
  updateDisplay();
}

// ---------------------------------------------------------------------------
// MAIN LOOP
// ---------------------------------------------------------------------------
void loop() {
  if (systemState == STATE_AP_MODE) {
    server.handleClient();
  }

  handleButton();

  unsigned long now = millis();

  switch (systemState) {

    case STATE_WAIT_SYNC:
      // waiting for the 5s hold, handled in handleButton()
      break;

    case STATE_AP_MODE:
      {
        bool timedOut = (now - apStartMillis) >= AP_TIMEOUT_MS;
        bool doneAfterSync = apSyncReceived && (now - apSyncReceivedMillis) >= 3000;
        if (timedOut || doneAfterSync) {
          stopApMode();
        }
        break;
      }

    case STATE_IDLE:
      if (timeSynced) {
        checkDailyReset();
        checkSchedule();
        QueueItem item;
        if (queuePop(item)) {
          activeMedIndex = item.medIndex;
          activeTimeIndex = item.timeIndex;
          startNextAlarm();
        }
      }
      break;

    case STATE_ALARM:
      updateBuzzer();
      if (now - alarmStartMillis >= ALARM_DURATION_MS) {
        markSkipped();
      }
      break;

    case STATE_BOX_CLOSING:
      if ((long)(now - boxCloseAtMillis) >= 0) {
        closeBox();
        systemState = STATE_IDLE;
      }
      break;
  }

  updateDisplay();
}

// ---------------------------------------------------------------------------
// BUTTON HANDLING (non-blocking, tracks press duration)
// ---------------------------------------------------------------------------
void handleButton() {
  bool raw = digitalRead(PIN_BUTTON);  // LOW = pressed (INPUT_PULLUP)
  unsigned long now = millis();

  // simple debounce
  if (raw != lastRawButtonState) {
    lastButtonChangeMillis = now;
    lastRawButtonState = raw;
  }
  bool stable = (now - lastButtonChangeMillis) > 30;

  if (stable) {
    bool isPressedNow = (raw == LOW);

    if (isPressedNow && !buttonPressed) {
      // press started
      buttonPressed = true;
      buttonPressStart = now;
      longPressTriggered = false;
    }

    if (isPressedNow && buttonPressed) {
      // still holding - check for long-press sync trigger
      unsigned long heldFor = now - buttonPressStart;
      if (!longPressTriggered && heldFor >= LONG_PRESS_SYNC_MS && (systemState == STATE_WAIT_SYNC || systemState == STATE_IDLE)) {
        longPressTriggered = true;
        startApMode();
      }
    }

    if (!isPressedNow && buttonPressed) {
      // release event
      unsigned long heldFor = now - buttonPressStart;
      buttonPressed = false;

      if (systemState == STATE_ALARM && heldFor < SHORT_PRESS_MAX_MS) {
        markTaken();
      }
      // long-press release with no state change (e.g. released before 5s) -> ignored
    }
  }
}

// ---------------------------------------------------------------------------
// TIME HELPERS
// ---------------------------------------------------------------------------
// Returns local wall-clock time (phone's UTC epoch + TIMEZONE_OFFSET_SEC),
// computed with gmtime() on the shifted epoch so we don't touch system TZ.
void getLocalTm(struct tm &out) {
  time_t rawNow = time(nullptr);
  time_t shifted = rawNow + TIMEZONE_OFFSET_SEC;
  gmtime_r(&shifted, &out);
}

// ---------------------------------------------------------------------------
// SCHEDULE CHECKING
// ---------------------------------------------------------------------------
void checkDailyReset() {
  struct tm t;
  getLocalTm(t);
  if (t.tm_yday != lastSeenDay) {
    lastSeenDay = t.tm_yday;
    skippedCount = 0;
    for (int m = 0; m < medicineCount; m++) {
      for (int ti = 0; ti < medicines[m].timesCount; ti++) {
        medicines[m].triggeredToday[ti] = false;
      }
    }
  }
}

void checkSchedule() {
  struct tm t;
  getLocalTm(t);
  if (t.tm_sec >= 10) return;  // only evaluate near the top of the minute

  long long nowMs = (long long)time(nullptr) * 1000LL;

  for (int m = 0; m < medicineCount; m++) {
    Medicine &med = medicines[m];

    long long rangeStart = med.startDate;
    long long rangeEnd = med.startDate + (long long)med.duration * 86400000LL;
    if (nowMs < rangeStart || nowMs >= rangeEnd) continue;

    for (int ti = 0; ti < med.timesCount; ti++) {
      if (med.triggeredToday[ti]) continue;

      int hh = (med.times[ti][0] - '0') * 10 + (med.times[ti][1] - '0');
      int mm = (med.times[ti][3] - '0') * 10 + (med.times[ti][4] - '0');

      if (hh == t.tm_hour && mm == t.tm_min) {
        med.triggeredToday[ti] = true;
        queuePush(m, ti);
      }
    }
  }
}

// ---------------------------------------------------------------------------
// QUEUE
// ---------------------------------------------------------------------------
bool queuePush(int medIndex, int timeIndex) {
  if (qCount >= MAX_QUEUE) return false;
  alarmQueue[qTail].medIndex = medIndex;
  alarmQueue[qTail].timeIndex = timeIndex;
  qTail = (qTail + 1) % MAX_QUEUE;
  qCount++;
  return true;
}

bool queuePop(QueueItem &out) {
  if (qCount == 0) return false;
  out = alarmQueue[qHead];
  qHead = (qHead + 1) % MAX_QUEUE;
  qCount--;
  return true;
}

// ---------------------------------------------------------------------------
// ALARM ACTIONS
// ---------------------------------------------------------------------------
void startNextAlarm() {
  Medicine &med = medicines[activeMedIndex];
  activeBox = med.box;  // 1-4

  systemState = STATE_ALARM;
  alarmStartMillis = millis();
  buzzerOn = false;
  buzzerToggleMillis = millis();

  int idx = activeBox - 1;
  if (idx >= 0 && idx < 4) {
    digitalWrite(LED_PINS[idx], HIGH);
    setServoAngle(SERVO_PINS[idx], SERVO_OPEN_ANGLE);
  }
}

void markTaken() {
  digitalWrite(PIN_BUZZER, LOW);
  systemState = STATE_BOX_CLOSING;
  boxCloseAtMillis = millis() + TAKEN_CLOSE_DELAY_MS;
}

void markSkipped() {
  digitalWrite(PIN_BUZZER, LOW);

  if (activeMedIndex >= 0 && skippedCount < MAX_SKIPPED) {
    Medicine &med = medicines[activeMedIndex];
    strncpy(skippedList[skippedCount].name, med.name, sizeof(skippedList[skippedCount].name) - 1);
    skippedList[skippedCount].name[sizeof(skippedList[skippedCount].name) - 1] = '\0';
    strncpy(skippedList[skippedCount].time, med.times[activeTimeIndex], sizeof(skippedList[skippedCount].time) - 1);
    skippedList[skippedCount].time[sizeof(skippedList[skippedCount].time) - 1] = '\0';
    skippedCount++;
  }

  systemState = STATE_BOX_CLOSING;
  boxCloseAtMillis = millis();  // close now
}

void closeBox() {
  int idx = activeBox - 1;
  if (idx >= 0 && idx < 4) {
    setServoAngle(SERVO_PINS[idx], SERVO_CLOSED_ANGLE);
    digitalWrite(LED_PINS[idx], LOW);
  }
  activeMedIndex = -1;
  activeTimeIndex = -1;
  activeBox = -1;
}

void updateBuzzer() {
  unsigned long now = millis();
  unsigned long interval = buzzerOn ? BUZZER_BEEP_ON_MS : BUZZER_BEEP_OFF_MS;
  if (now - buzzerToggleMillis >= interval) {
    buzzerOn = !buzzerOn;
    buzzerToggleMillis = now;
    digitalWrite(PIN_BUZZER, buzzerOn ? HIGH : LOW);
  }
}

// ---------------------------------------------------------------------------
// WIFI / AP MODE / WEB SERVER
// ---------------------------------------------------------------------------
void startApMode() {
  systemState = STATE_AP_MODE;
  apStartMillis = millis();
  apSyncReceived = false;

  WiFi.mode(WIFI_AP);
  if (strlen(AP_PASSWORD) == 0) {
    WiFi.softAP(AP_SSID);
  } else {
    WiFi.softAP(AP_SSID, AP_PASSWORD);
  }

  server.on("/", HTTP_GET, handleRoot);
  server.on("/sync", HTTP_POST, handleSync);
  server.begin();
}

void stopApMode() {
  server.stop();
  WiFi.softAPdisconnect(true);
  WiFi.mode(WIFI_OFF);
  systemState = timeSynced ? STATE_IDLE : STATE_WAIT_SYNC;
}

void handleRoot() {
  server.send(200, "text/plain", "ESPMEDALARM ready. POST medicine data to /sync");
}

void handleSync() {
  if (!server.hasArg("plain")) {
    server.send(400, "text/plain", "Missing body");
    return;
  }
  String body = server.arg("plain");

  JsonDocument doc;
  DeserializationError err = deserializeJson(doc, body);
  if (err) {
    server.send(400, "text/plain", "Bad JSON");
    return;
  }

  // --- sync time from phone ---
  long long currentTime = doc["currentTime"] | 0LL;
  if (currentTime > 0) {
    struct timeval tv;
    tv.tv_sec = (time_t)(currentTime / 1000LL);
    tv.tv_usec = (suseconds_t)((currentTime % 1000LL) * 1000LL);
    settimeofday(&tv, nullptr);
    timeSynced = true;
    lastSeenDay = -1;  // force a fresh daily-reset evaluation with the new clock
  }

  // --- load medicines ---
  JsonArray meds = doc["medicines"].as<JsonArray>();
  medicineCount = 0;

  for (JsonObject m : meds) {
    if (medicineCount >= MAX_MEDS) break;

    Medicine &med = medicines[medicineCount];
    med.id = m["id"] | 0;

    const char *name = m["name"] | "Medicine";
    strncpy(med.name, name, sizeof(med.name) - 1);
    med.name[sizeof(med.name) - 1] = '\0';

    med.box = m["box"] | 1;
    if (med.box < 1) med.box = 1;
    if (med.box > 4) med.box = 4;

    med.duration = m["duration"] | 0;
    med.startDate = m["startDate"] | 0LL;

    med.timesCount = 0;
    JsonArray times = m["times"].as<JsonArray>();
    for (JsonVariant tv : times) {
      if (med.timesCount >= MAX_TIMES_PER_MED) break;
      const char *tstr = tv.as<const char *>();
      if (tstr && strlen(tstr) >= 5) {
        strncpy(med.times[med.timesCount], tstr, 5);
        med.times[med.timesCount][5] = '\0';
        med.triggeredToday[med.timesCount] = false;
        med.timesCount++;
      }
    }

    medicineCount++;
  }

  apSyncReceived = true;
  apSyncReceivedMillis = millis();

  server.send(200, "text/plain", "OK");
}

// ---------------------------------------------------------------------------
// DISPLAY
// ---------------------------------------------------------------------------
void formatTime12Hour(const char *time24, char *output, size_t outputSize) {

  int hour, minute;

  if (sscanf(time24, "%d:%d", &hour, &minute) != 2) {
    strncpy(output, time24, outputSize);
    output[outputSize - 1] = '\0';
    return;
  }

  int hour12 = hour % 12;
  if (hour12 == 0) hour12 = 12;

  const char *ampm = (hour < 12) ? "AM" : "PM";

  snprintf(output, outputSize, "%02d:%02d %s", hour12, minute, ampm);
}
void updateDisplay() {
  unsigned long now = millis();
  if (now - lastDisplayMillis < 250) return;  // ~4fps is plenty
  lastDisplayMillis = now;

  u8g2.clearBuffer();

  switch (systemState) {
    case STATE_WAIT_SYNC:
      drawWaitSync();
      break;
    case STATE_AP_MODE:
      drawApMode();
      break;
    case STATE_ALARM:
      drawAlarm();
      break;
    case STATE_IDLE:
    case STATE_BOX_CLOSING:
    default:
      drawIdle();
      break;
  }

  u8g2.sendBuffer();
}

void drawWaitSync() {
  u8g2.setFont(u8g2_font_6x12_tr);
  u8g2.drawStr(0, 20, "press button for 5sec");
  u8g2.drawStr(0, 34, "to sync");
}

void drawApMode() {
  unsigned long remainingMs = 0;
  unsigned long elapsed = millis() - apStartMillis;
  if (elapsed < AP_TIMEOUT_MS) remainingMs = AP_TIMEOUT_MS - elapsed;
  int remainingSec = remainingMs / 1000;

  u8g2.setFont(u8g2_font_6x12_tr);
  u8g2.drawStr(0, 12, "WiFi Sync Mode");
  u8g2.drawStr(0, 26, "Connect phone to:");
  u8g2.drawStr(0, 38, AP_SSID);
  u8g2.drawStr(0, 50, AP_PASSWORD);

  char buf[24];
  if (apSyncReceived) {
    snprintf(buf, sizeof(buf), "Synced!");
  } else {
    snprintf(buf, sizeof(buf), "Closes in %ds", remainingSec);
  }
  u8g2.drawStr(0, 62, buf);
}

void drawIdle() {
  struct tm t;
  getLocalTm(t);

  // Convert 24hr tm_hour to 12hr with AM/PM
  int hour12 = t.tm_hour % 12;
  if (hour12 == 0) hour12 = 12;
  const char *ampm = (t.tm_hour < 12) ? "AM" : "PM";

  char timeBuf[12];
  snprintf(timeBuf, sizeof(timeBuf), "%02d:%02d %s", hour12, t.tm_min, ampm);

  u8g2.setFont(u8g2_font_logisoso20_tr);
  u8g2.drawStr(10, 24, timeBuf);

  u8g2.setFont(u8g2_font_6x12_tr);
  if (skippedCount == 0) {
    u8g2.drawStr(0, 40, "No skipped meds today");
  } else {
    u8g2.drawStr(0, 40, "Skipped today:");
    int y = 52;
    int shown = 0;
    for (int i = 0; i < skippedCount && shown < 2; i++, shown++) {

      char displayTime[12];
      formatTime12Hour(skippedList[i].time, displayTime, sizeof(displayTime));

      char line[32];
      snprintf(line, sizeof(line), "%s %s", displayTime, skippedList[i].name);

      u8g2.drawStr(0, y, line);
      y += 12;
    }
  }
}

void drawAlarm() {
  Medicine &med = medicines[activeMedIndex];

  u8g2.setFont(u8g2_font_7x14B_tr);
  u8g2.drawStr(0, 14, "TAKE MEDICINE");

  u8g2.setFont(u8g2_font_6x12_tr);
  char line[32];
  snprintf(line, sizeof(line), "Name: %s", med.name);
  u8g2.drawStr(0, 30, line);

  snprintf(line, sizeof(line), "Box: %d", med.box);
  u8g2.drawStr(0, 42, line);

  unsigned long elapsed = millis() - alarmStartMillis;
  unsigned long remaining = (elapsed < ALARM_DURATION_MS) ? (ALARM_DURATION_MS - elapsed) : 0;
  int remSec = remaining / 1000;
  snprintf(line, sizeof(line), "Auto-skip in %ds", remSec);
  u8g2.drawStr(0, 54, line);

  u8g2.drawStr(0, 64, "Short press = taken");
}
