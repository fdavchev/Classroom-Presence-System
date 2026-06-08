# 📋 Classroom Presence System (CPS)

A contactless student attendance system powered by **NFC / HCE (Host Card Emulation)**. Students tap their Android phone on the teacher's device to check in — no paper, no manual roll calls.

---

## ⚙️ How It Works

1. Student logs in with their university email via **Firebase Authentication**
2. The backend issues a **JWT token** storing student ID, name, and course
3. In class, the student holds their phone near the teacher's device
4. The **HCE service** broadcasts the student payload over NFC
5. The teacher's device reads the data and records attendance
6. The teacher views live attendance in the **web dashboard**

---

## 🧩 Tech Stack

| Layer     | Technology                              |
|-----------|-----------------------------------------|
| Mobile    | Kotlin, Android HCE, Firebase Auth      |
| Backend   | Python 3.10+, FastAPI, Firebase Admin SDK |
| Auth      | Firebase Authentication + JWT           |
| Database  | Firebase Firestore                      |
| Dashboard | HTML, CSS, JavaScript                   |

---

## 🏗️ Project Structure

```
Classroom-Presence-System/
├── backend/          # Python / FastAPI REST API
├── dashboard/        # Web dashboard (HTML/CSS/JS)
├── teacher-app/      # Android app for teachers (Kotlin)
├── student-app/      # Android app for students (Kotlin)
└── docs/             # Documentation
```

## ⚡ Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/fdavchev/Classroom-Presence-System.git
cd Classroom-Presence-System

# 2. Set up the backend
cd backend
pip install -r requirements.txt

# 3. Add your Firebase service account key
# Place serviceAccountKey.json in backend/

# 4. Start the API server
python main.py
# → Running at http://localhost:8000
```

Open `dashboard/dashboard.html` in a browser to access the teacher dashboard.

For the Android apps, see [Mobile Setup](#-mobile-setup) below.

---


## 📦 Prerequisites

- **Python 3.10+** with pip
- **Android Studio** (for building mobile apps)
- **Physical Android device** with NFC support — emulators won't work for HCE
- A **Firebase project** with Authentication and Firestore enabled
- `google-services.json` — download from your Firebase project console
- `serviceAccountKey.json` — download from Firebase → Project Settings → Service Accounts

---

## 📱 Mobile Setup

### Student App

1. Open the `student-app` folder in Android Studio
2. Set your backend IP in `LoginActivity.kt`:
   ```kotlin
   private val BASE_URL = "http://YOUR_IP:8000/"
   ```
3. Place `google-services.json` in `student-app/app/`
4. Build and run on a physical Android device

### Teacher App

1. Open the `teacher-app` folder in Android Studio
2. Set your backend IP the same way as above
3. Place `google-services.json` in `teacher-app/app/`
4. Build and run on a physical Android device with NFC enabled

---

## 🔑 Firebase Setup

1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project
2. Enable **Email/Password** authentication under Authentication → Sign-in method
3. Create a **Firestore** database
4. Download `google-services.json` (for the Android apps) and `serviceAccountKey.json` (for the backend)
5. Place each file in the correct location as described above

---

## 🖥️ Dashboard

Open `dashboard/dashboard.html` in any browser while the backend is running. Teachers can:
- View attendance per class and session
- Monitor check-ins in real time

---

## 👥 Contributors

| Name               | GitHub                                      |
|--------------------|---------------------------------------------|
| Filip Davchev      | [@fdavchev](https://github.com/fdavchev)   |
| Simona Zlatanovska | [@sims03](https://github.com/sims03)        |

---

## 📄 License

This project is open source. Educational purposes.
