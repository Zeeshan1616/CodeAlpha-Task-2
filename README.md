# CodeAlpha-Task-2
This is the Task no.2 of the Java Programming Internship by @CodeAlpha
# 🏨 The Elite Hotel — Reservation Management System

> A full-featured hotel reservation desktop application built with Java Swing, featuring dual-portal access, persistent storage, and a polished UI design system.

---

## Overview

**The Elite Hotel** is a Java desktop application that simulates a real-world hotel reservation workflow. It provides separate portals for guests and hotel administrators, with persistent local storage, room availability logic, and simulated payment processing — all wrapped in a custom-designed Swing UI.

---

## Features

### 🔐 Authentication
- Dual-login screen with **Guest** and **Admin** tabs
- Hardcoded credential validation (configurable)
- Smooth card-based layout with tab switching

### 🛎️ Guest Portal
| Feature | Description |
|---|---|
| **Search Rooms** | Filter available rooms by type and date range |
| **Book a Room** | Reserve any available room with full guest details |
| **My Bookings** | Look up reservations by name; cancel confirmed bookings |
| **Pay for Booking** | Simulated 16-digit card payment with booking ID lookup |

### 🖥️ Admin Portal
| Feature | Description |
|---|---|
| **Dashboard** | Live stats — total rooms, confirmed/cancelled counts, revenue |
| **All Reservations** | Full booking table with cancel and bulk-clear actions |
| **Room Inventory** | View all 20 rooms; check availability for any date range |
| **Manual Booking** | Create bookings on behalf of guests; optional instant payment |

---

## Room Categories

| Type | Rooms | Price/Night | Description |
|---|---|---|---|
| **Standard** | 101 – 110 | $80 | Essential amenities, cozy comfort |
| **Deluxe** | 201 – 207 | $150 | Premium furnishings, city view |
| **Suite** | 301 – 303 | $300 | Luxury suite, jacuzzi & butler |

---

## Tech Stack

- **Language:** Java 11+
- **UI Framework:** Java Swing (custom design system)
- **Persistence:** Java Object Serialization (`.dat` files)
- **Storage Location:** `~/.elitehotel/` (created automatically)
- **Build:** Single-file compilation, no external dependencies

---

## Getting Started

### Prerequisites
- Java Development Kit (JDK) **11 or higher**
- Any OS with a graphical desktop environment (Windows, macOS, Linux)

### Compile

```bash
javac InternshipTasks/Task2.java
```

### Run

```bash
java InternshipTasks.Task2
```

---

## Default Credentials

| Portal | Username | Password |
|---|---|---|
| Guest | `guest` | `1234` |
| Admin | `admin` | `admin123` |

> ⚠️ These are hardcoded for demonstration purposes. Do not use in production environments.

---

## Project Structure

```
InternshipTasks/
└── Task2.java
    ├── Room                  # Room model (type, price, description)
    ├── Reservation           # Reservation model with status & payment
    ├── DataStore             # File-based serialization layer
    ├── HotelService          # Core business logic (booking, cancellation, payment)
    ├── DS                    # Design tokens (colors, fonts, formatters)
    ├── RoundBtn              # Custom rounded button widget
    ├── FlatField             # Custom text field with placeholder support
    ├── FlatPass              # Custom password field
    ├── Card                  # Styled panel container
    ├── PageHeader            # Consistent page title component
    ├── StatCard              # Dashboard metric card
    ├── TF                    # Table factory (styling + scroll pane)
    ├── Base                  # Abstract base frame with shared helpers
    ├── LoginScreen           # Dual-tab login UI
    ├── GuestDashboard        # Full guest-facing dashboard
    ├── AdminDashboard        # Full admin-facing dashboard
    └── Task2                 # Entry point (main method)
```

---

## Data Persistence

Reservation and room data are stored locally using Java's built-in object serialization:

```
~/.elitehotel/
├── reservations.dat    # All booking records
└── rooms.dat           # Room inventory (initialized once)
```

Data persists across application restarts. The **Clear All Records** action in the Admin panel permanently deletes `reservations.dat`.

---

## Screenshots

> _Add screenshots of the Login Screen, Guest Dashboard, and Admin Dashboard here._

---

## Limitations & Notes

- Payment processing is **simulated** — no real transactions occur
- Credentials are **hardcoded** — not suitable for production use as-is
- Designed as a **single-file educational project** for internship/academic purposes
- No multi-user concurrency handling

---

## License

This project was developed as part of an internship task. Redistribution and modification are permitted for educational purposes.

---

<p align="center">Made with Java Swing &nbsp;·&nbsp; The Elite Hotel &nbsp;·&nbsp; <em>Where Comfort Meets Excellence</em></p>
