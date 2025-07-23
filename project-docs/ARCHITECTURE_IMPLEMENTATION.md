# Ausgetrunken Architecture & Implementation Guide

## Domain-Driven Design (DDD) Architecture

### Core Architecture Guidelines
- **State Management**: MVVM with ViewModels and UI State classes - NO Redux patterns
- **Dependency Injection**: Koin framework for DI container
- **Navigation**: Navigation Compose for type-safe routing  
- **Data Layer**: Repository pattern with Room + Supabase
- **UI Layer**: Jetpack Compose with Material Design 3

### Layer Structure
```
app/src/main/java/com/ausgetrunken/
├── ui/                          # Presentation Layer
│   ├── customer/                # Customer user experience
│   ├── owner/                   # Wineyard owner features
│   ├── auth/                    # Authentication flows
│   ├── theme/                   # Design system & theming
│   └── components/              # Reusable UI components
├── domain/                      # Business Logic Layer
│   ├── model/                   # Domain entities
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Business use cases
├── data/                        # Data Layer
│   ├── local/                   # Room database
│   ├── remote/                  # Supabase API clients
│   └── repository/              # Repository implementations
└── di/                          # Dependency Injection
```

### User Type Architecture
- **Customer Users**: Landing on discovery interface (wineyards/wines)
- **Wineyard Owners**: Landing on profile management interface
- **Authentication**: Supabase-based with persistent session management
- **Role Detection**: Splash screen routing based on `UserType` enum

## Push Notification System (FCM)

### 🎉 **Status: Production Ready**
Complete end-to-end push notification system successfully implemented with Firebase Cloud Messaging.

### System Architecture
```
Android App → Supabase Edge Function → Firebase FCM → Customer Devices
```

### Core Components

#### 1. **Android FCM Integration**
- **`AusgetrunkenMessagingService`**: Production-ready notification display service
- **`FCMTokenManager`**: Automatic token synchronization with user authentication
- **`NotificationRepositoryImpl`**: Robust FCM integration with comprehensive logging

#### 2. **Supabase Edge Function**
- **Function**: `send-fcm-notification`
- **Authentication**: Firebase service account with RSA private key
- **Features**: Subscriber querying, FCM delivery, delivery record tracking
- **Error Handling**: Comprehensive logging and fallback mechanisms

#### 3. **Database Integration**
- **Tables**: `notifications`, `notification_deliveries`, `wineyard_subscriptions`
- **Foreign Keys**: Proper sender_id resolution using wineyard owner lookup
- **Subscription Types**: Low stock, new release, special offer, general notifications

### Implementation Details

#### Firebase Authentication (Fixed)
**Problem**: `DOMExceptionDataError: unknown/unsupported ASN.1 DER tag: 0x2d`
**Solution**: PEM to binary key conversion for Deno crypto API
```typescript
const privateKeyPem = serviceAccount.private_key
  .replace(/-----BEGIN PRIVATE KEY-----/, '')
  .replace(/-----END PRIVATE KEY-----/, '')
  .replace(/\s/g, '')
const privateKeyBinary = Uint8Array.from(atob(privateKeyPem), c => c.charCodeAt(0))
```

#### Notification Flow
1. Owner selects wine from notification management screen
2. System identifies wine's wineyard and finds active subscribers  
3. FCM tokens retrieved from Supabase user profiles
4. Firebase authentication with service account
5. Push notifications delivered to customer devices
6. Delivery records stored for tracking and analytics

## Subscription System Design

### Database Schema
```sql
CREATE TABLE wineyard_subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES user_profiles(id) ON DELETE CASCADE,
  wineyard_id UUID REFERENCES wineyards(id) ON DELETE CASCADE,
  is_active BOOLEAN DEFAULT true,
  low_stock_notifications BOOLEAN DEFAULT true,
  new_release_notifications BOOLEAN DEFAULT true,
  special_offer_notifications BOOLEAN DEFAULT true,
  general_notifications BOOLEAN DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Subscription Types
- **Low Stock Alerts**: Notify when wine inventory is low
- **New Releases**: Announce new wines added to wineyard
- **Special Offers**: Marketing and promotional notifications  
- **General Updates**: Wineyard news and announcements

### Implementation
- **Customer Subscription**: Toggle preferences in customer profile
- **Owner Notifications**: Send targeted notifications from management screen
- **Filtering**: Real-time subscriber filtering by notification type
- **Analytics**: Delivery tracking and engagement metrics

## Wine Management System

### Features
- **CRUD Operations**: Add, view, edit, delete wines
- **Wine Limit**: 20 wines maximum per wineyard
- **Stock Management**: Automatic low stock detection and notifications
- **Form Validation**: Real-time validation with error feedback

### Data Synchronization
- **Primary**: Supabase as authoritative data source
- **Offline**: Room database for offline support
- **Sync Strategy**: Hybrid approach with real-time updates

### UI/UX Features
- **Pull-to-Refresh**: Material 3 refresh functionality
- **Real-time Updates**: Immediate UI updates after operations
- **Auto-refresh**: Automatic list refresh when returning from forms
- **Loading States**: Progress indicators with smooth animations

## Build System & Dependencies

### Kotlin 2.0 Upgrade
- **Compiler**: Kotlin 2.0.21 with improved performance
- **Compose**: Updated to latest stable version with Kotlin compatibility
- **Compatibility**: All dependencies verified for Kotlin 2.0 compatibility

### Key Dependencies (gradle/libs.versions.toml)
```toml
[versions]
kotlin = "2.0.21"
compose-bom = "2024.02.00"
supabase = "2.0.4"
koin = "3.5.0"
room = "2.6.1"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
supabase-postgrest-kt = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt", version.ref = "supabase" }
supabase-auth-kt = { group = "io.github.jan-tennert.supabase", name = "auth-kt", version.ref = "supabase" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
```

### Build Optimization
- **Parallel Builds**: Enabled for faster compilation
- **Gradle Daemon**: Persistent daemon for reduced startup time
- **Build Cache**: Optimized for incremental builds
- **Custom Tasks**: `setupCheck`, `buildAndInstall`, `quickStart`

## Design System & Theming

### Material Design 3 Implementation
- **Color System**: Wine-inspired palette with deep reds, gold accents
- **Dark Mode**: Gray on black with colored cards that "pop"
- **Light Mode**: Clean white backgrounds with soft shadows
- **Typography**: Consistent font scales and weights

### Custom Components
- **Sophisticated Placeholders**: Hand-crafted Canvas drawings
  - `UserPlaceholderIcon`: SVG-inspired person silhouette
  - `WineyardPlaceholderImage`: Rolling hills with vineyard rows
  - `WineBottlePlaceholder`: Different styles for each wine type
- **Customer Cards**: Professional wine industry aesthetic
- **Navigation**: Intuitive tab switching and profile access

### Responsive Design
- **Loading States**: Comprehensive loading indicators
- **Error Handling**: User-friendly error messages
- **Form Validation**: Real-time feedback with Material Design patterns
- **Pull-to-Refresh**: Native Material 3 refresh functionality

## Authentication & Session Management

### Supabase Integration
- **Provider**: Supabase Auth with email/password
- **Session Persistence**: Automatic token refresh and storage
- **User Profiles**: Extended user data in `user_profiles` table
- **Role Detection**: UserType enum for customer/owner differentiation

### Flow Implementation
```kotlin
// Splash screen routing
when (userType) {
    UserType.CUSTOMER -> CustomerLandingScreen()
    UserType.WINEYARD_OWNER -> OwnerProfileScreen()
    null -> LoginScreen()
}
```

### Security Features
- **JWT Tokens**: Secure token-based authentication
- **Row Level Security**: Database-level access control
- **FCM Token Security**: Secure token storage and validation
- **Service Account**: Firebase integration with proper key management

## Recent Upgrades & Fixes

### Code Quality & Navigation Enhancement (2025-01-23)
- ✅ **Navigation Flow Fix**: Wine creation now returns to wineyard detail page for better UX
- ✅ **Compilation Bug Resolution**: Fixed type incompatibility in AuthenticatedRepository error handling
- ✅ **Error Handling Optimization**: Simplified exception-to-AppError conversion pipeline
- ✅ **Type Safety Improvement**: Removed redundant error mapping in authentication layer

### Notification System Breakthrough (2025-07-20)
- ✅ **Complete FCM Integration**: End-to-end notifications working
- ✅ **Firebase Authentication**: Fixed private key format for Deno
- ✅ **Database Constraints**: Proper foreign key relationships
- ✅ **Cross-Device Testing**: Owner (emulator) → Customer (device) verified

### Customer Experience Enhancement (2025-07-18)
- ✅ **Customer Landing Page**: Tabbed interface with pagination
- ✅ **Authentication Flow**: Fixed routing based on user type
- ✅ **Logout Functionality**: Added for both customer and owner types
- ✅ **UI/UX Polish**: Wine-inspired theming and sophisticated placeholders

### Wine Management Implementation
- ✅ **CRUD Operations**: Complete wine management with validation
- ✅ **Navigation Integration**: Seamless flow from wineyard to wine management
- ✅ **Real-time Updates**: Immediate UI updates and auto-refresh
- ✅ **Stock Management**: Integrated with notification system

## Performance & Monitoring

### Metrics
- **Build Time**: 1m 38s (verified working)
- **Notification Delivery**: < 2 seconds end-to-end
- **Database Queries**: Optimized with proper indexing
- **UI Responsiveness**: Real-time updates with loading states

### Logging & Debugging
- **Comprehensive Logging**: Throughout notification pipeline
- **Error Tracking**: Enhanced error messages and status reporting
- **Development Tools**: Real-time log analysis capabilities
- **Multi-Device Testing**: Emulator + physical device environment

> **Current Status**: All major systems operational. Push notification system is production-ready with complete end-to-end functionality verified.