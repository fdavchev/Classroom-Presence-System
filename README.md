# 📋 Classroom Presence System (CPS)

A mobile-based student attendance system using **NFC / HCE (Host Card Emulation)** for contactless check-in. Students tap their phone on the teacher's device to mark attendance — no paper, no manual roll calls.

---

## 🏗️ Project Structure

```
Classroom-Presence-System/
├── backend/          # Python/FastAPI REST API
├── dashboard/        # Web dashboard for teachers/admins
├── teacher-app/      # Android app for teachers (Kotlin)
├── student-app/      # Android app for students (Kotlin)
└── docs/             # Documentation
```

---

## ⚙️ How It Works

1. Student logs in to the Android app using their university email via Firebase Authentication
2. The app receives a JWT token from the backend and stores a payload (student ID, name, course)
3. In class, the student holds their phone near the teacher's NFC reader
4. The HCE service broadcasts the student payload
5. The teacher's device reads the NFC data and marks attendance in the system
6. The teacher can view attendance in real time via the web dashboard

---

## 🧩 Tech Stack

| Layer | Technology |
|---|---|
| Mobile App | Kotlin, Android HCE, Firebase Auth |
| Backend | Python, FastAPI, Firebase Admin SDK |
| Auth | Firebase Authentication + JWT |
| Database | Firebase Firestore |
| Dashboard | HTML, CSS, JavaScript |

---

## 🚀 Running the Backend

**Requirements:** Python 3.10+, pip

```bash
cd backend
pip install -r requirements.txt
python main.py
```

The API runs on `http://localhost:8000` by default.

---

## 📱 Running the Student App

1. Open the `student-app` folder in Android Studio
2. Set your backend IP in `LoginActivity.kt`:
   ```kotlin
   private val BASE_URL = "http://YOUR_IP:8000/"
   ```
3. Add your `google-services.json` to `student-app/app/`
4. Build and run on a physical Android device (NFC/HCE requires real hardware)

---

## 🖥️ Dashboard

Open `dashboard/dashboard.html` in a browser while the backend is running. Teachers can view attendance records per class and session.

---

## 📦 Requirements

- Physical Android device with NFC support (emulator won't work for HCE)
- Python 3.10+
- Firebase project with Authentication and Firestore enabled
- `google-services.json` placed in `student-app/app/`
- `serviceAccountKey.json` placed in `backend/` (not included in repo)

---

## Contributors

| Name | GitHub |
|------|--------|
| Filip Davchev | [@fdavchev](https://github.com/fdavchev) |
| Simona Zlatanovska | [@sims03](https://github.com/sims03) |


## License

This project is open source. See the repository for licensing details.
