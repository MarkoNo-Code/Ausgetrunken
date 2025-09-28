# Ausgetrunken 🍷

A sophisticated Android application connecting wine enthusiasts with local winerys. Built with modern Android technologies including Jetpack Compose, Material3 design, and Firebase backend services.

## Project Overview

Ausgetrunken is a native Android app that bridges the gap between winery owners and wine lovers. The platform allows winery owners to showcase their products and manage inventory, while customers can discover local winerys, browse wine catalogs, and receive notifications about their favorite wines.

## Features

### 🔐 Authentication System
- **Two User Types**: Wineyard owners and customers
- **Firebase Authentication**: Secure sign-up and sign-in functionality
- **User Type Management**: Role-based access control

### 🏭 Wineyard Owner Features
- **Profile Management**: Create and maintain winery information
- **Photo Gallery**: Add up to 3 photos for winery showcase
- **Product Catalog**: Comprehensive wine management system
- **Wine Details**: Add descriptions, prices, discounted prices, vintage information
- **Inventory Management**: Manual stock quantity adjustments
- **Smart Notifications**: Automatic alerts when stock hits 20% threshold
- **Location Services**: Add winery geographical location

### 🍷 Customer Features
- **Discovery**: Search and browse local winerys
- **Detailed Views**: Explore winery profiles and wine catalogs
- **Subscription System**: Subscribe to specific wines for stock notifications
- **Interactive Map**: View all winerys in the area with location-based services
- **Personalized Alerts**: Receive notifications when subscribed wines are low in stock

### 🎨 Design & User Experience
- **Sophisticated Premium Look**: Professional, elegant interface
- **Wine-Inspired Color Scheme**:
  - Slate black base (#2F2F2F)
  - Dark brownish-tan (#8B5A3C)
  - Wine red accents (#722F37)
  - Gold highlights (#D4AF37)
- **Material3 Components**: Modern Android design system
- **Responsive UI**: Optimized for various screen sizes

## Technical Architecture

### 🏗️ Architecture Pattern
- **MVVM (Model-View-ViewModel)**: Clean separation of concerns
- **Repository Pattern**: Abstracted data layer
- **Use Cases**: Business logic encapsulation
- **Dependency Injection**: Koin framework

### 📱 Android Technologies
- **Minimum SDK**: Android 12 (API 31)
- **Target SDK**: Android 14 (API 34)
- **UI Framework**: Jetpack Compose
- **Navigation**: Compose Navigation
- **State Management**: StateFlow with ViewModels

### 🗄️ Data Management
- **Local Database**: Room for offline storage
- **Remote Backend**: Firebase Firestore
- **Image Storage**: Firebase Storage
- **Type Converters**: Gson for complex data serialization

### 🔔 Notification System
- **Firebase Cloud Messaging**: Push notifications
- **Smart Inventory Alerts**: Automated low stock notifications
- **Subscription Management**: User-specific wine alerts
- **GSM Integration**: Native Android notification handling

### 🌍 Location & Mapping
- **Google Play Services**: Location services integration
- **Maps Integration**: Interactive winery discovery
- **Proximity Search**: Find nearby winerys
- **Location Permissions**: Fine and coarse location access

### 📊 Analytics & Monitoring
- **Firebase Analytics**: User behavior tracking
- **Firebase Crashlytics**: Crash reporting and monitoring
- **Extensible Tracking**: Architecture ready for additional analytics

### 🔧 Development Tools
- **Kotlin**: Primary programming language
- **Version Catalog**: Centralized dependency management
- **KSP**: Kotlin Symbol Processing for Room
- **Build System**: Gradle with Kotlin DSL

## Project Structure

```
app/src/main/java/com/ausgetrunken/
├── auth/                    # Authentication logic
├── data/
│   ├── local/              # Room database
│   │   ├── entities/       # Database entities
│   │   └── dao/           # Data Access Objects
│   ├── remote/            # Firebase integration
│   └── repository/        # Repository implementations
├── domain/
│   └── usecase/           # Business logic use cases
├── di/                    # Dependency injection modules
├── notifications/         # FCM and notification handling
└── ui/
    ├── navigation/        # Compose navigation
    └── theme/            # Material3 theming
```

## Implementation Status

### ✅ Completed Features
- [x] Project setup with MVVM architecture
- [x] Dependency injection with Koin
- [x] Room database with entities and DAOs
- [x] Firebase authentication system
- [x] Repository pattern implementation
- [x] Use cases for business logic
- [x] Material3 theme with custom colors
- [x] Navigation structure with Compose
- [x] Notification system foundation
- [x] Vector drawable icons

### 🚧 Pending Implementation
- [ ] Authentication UI screens (Login/Register)
- [ ] Wineyard owner dashboard
- [ ] Wine catalog management screens
- [ ] Customer discovery interface
- [ ] Interactive map integration
- [ ] Camera integration for photo uploads
- [ ] Push notification UI handling
- [ ] Search and filtering functionality
- [ ] Subscription management UI
- [ ] Profile management screens
- [ ] Stock management interface
- [ ] Location-based winery discovery
- [ ] Image upload and display system

## Getting Started

### Prerequisites
- Android Studio Hedgehog | 2023.1.1 or newer
- Android SDK 34
- Kotlin 1.9.22
- Firebase project setup

### Installation
1. Clone the repository
2. Open in Android Studio
3. Add your `google-services.json` file to the `app/` directory
4. Sync project dependencies
5. Build and run on Android 12+ device or emulator

### Firebase Setup
1. Create a new Firebase project
2. Enable Authentication, Firestore, Storage, and Cloud Messaging
3. Download `google-services.json` and place in `app/` directory
4. Configure authentication providers as needed

## Contributing

This project follows clean architecture principles and Material Design guidelines. When contributing:
- Follow the established MVVM pattern
- Use the defined color scheme and theming
- Implement proper error handling
- Add appropriate unit tests
- Follow Kotlin coding conventions

## License

This project is developed for educational and portfolio purposes.

---

**Technologies**: Android, Kotlin, Jetpack Compose, Firebase, Room, Material3, Koin
**Architecture**: MVVM, Clean Architecture, Repository Pattern
**Target**: Android 12+ (API 31+)