# MedAlarm ESP32

MedAlarm is a simple Android medicine reminder application that works together with an **ESP32-S3 powered automated medicine box**. The system reminds users when it's time to take their medication and automatically opens the correct medicine compartment.

This project combines an Android application with embedded hardware to create an easy-to-use smart medication management system.

## Features

### Android App
- Add, edit, and delete medicines
- Multiple reminder times per medicine
- Assign medicines to specific storage boxes
- Daily alarm notifications
- Medicine duration tracking
- Local Room Database storage
- Clean Material Design interface

### ESP32-S3 Medicine Box
- Receives commands from the Android app
- Automatically opens the assigned medicine compartment
- OLED display for status information
- Designed for reliable daily medication reminders

---

## Tech Stack

### Android
- Java
- Android Studio
- Room Database
- RecyclerView
- AlarmManager
- Material Components

### Hardware
- ESP32-S3 N16R8
- SH1106 128×64 OLED Display
- Servo Motors
- I2C Communication

---

## Screenshots

>will add later

---

## How It Works

1. Add your medicines in the Android app.
2. Assign each medicine to a compartment in the medicine box.
3. Set reminder times.
4. When it's time:
   - The Android app triggers a reminder.
   - The ESP32-S3 receives the command.
   - The correct medicine compartment opens automatically.

---

## Project Structure

Android App/
ESP32 Firmware/
README.md


## Future Improvements

- Bluetooth Low Energy (BLE) communication
- Wi-Fi synchronization
- Caregiver notifications
- Medicine history logs
- Cloud backup
- Battery status monitoring

---

## Acknowledgements

This project was developed by **Himel Mahmud ♞** as a learning and engineering project combining Android development with embedded systems.

AI tools, including **Claude** and **ChatGPT**, were used to assist with code generation, debugging, and documentation. All design decisions, integration, testing, and final implementation were completed by the project author.

---

## 📄 License

This project is open source and available under the MIT License.
