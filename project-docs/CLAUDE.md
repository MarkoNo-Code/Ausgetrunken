# Claude Code Configuration

This file configures Claude Code for the Ausgetrunken project.

> 🔧 **IMPORTANT for Future Sessions**: Always check files in `project-docs/` first! 
> Setup scripts, documentation, and configuration files are organized here to keep the project root clean.

> 🚨 **CRITICAL**: Before making ANY authentication/session changes, read `project-docs/critical-features.md` FIRST!  
> Session persistence has broken multiple times and must never be broken again!

> 📁 **FILE ORGANIZATION RULE**: Read `project-docs/file-organization-rules.md` IMMEDIATELY!  
> Never put .js, .sql, node_modules, supabase/, or other non-Android files in the root directory!

## 🚨 SESSION STARTUP: MCP Configuration Required

**CRITICAL**: Load MCP configuration at session start for full functionality:

```
Load MCP configuration from C:\Users\marko\Documents\Claude-Projects\MCP\FINAL-MCP-CONFIG.json
```

This enables:
- **Android Studio MCP**: Direct builds, device management, gradle operations
- **Supabase MCP**: Real-time database queries, schema inspection, notification debugging

**Expected MCP Tools After Loading**:
- `android_studio_*` tools (build, clean, sync, test, lint)
- `adb_*` tools (devices, install, logs) 
- `mcp_supabase_*` tools (query, table_info, list_tables)

> ⚠️ **If MCP tools are not available**: Session was started without MCP configuration. Restart Claude Code with the MCP config file.

## Working Directory
This project is located at: `C:\Users\marko\Documents\Claude-Projects\Ausgetrunken`

## Project Information
- Project Name: Ausgetrunken
- Type: Android Application (Kotlin + Compose)
- Backend: Supabase
- Default working directory set for Claude Code sessions

## Architecture Guidelines
- **State Management**: Use MVVM with ViewModels and UI State classes - NO Redux patterns
- **Dependency Injection**: Koin framework for DI container
- **Navigation**: Navigation Compose for type-safe routing
- **Data Layer**: Repository pattern with Room + Supabase
- **UI Layer**: Jetpack Compose with Material Design 3

## Android Development Setup

### Required Environment Variables
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Users/marko/AppData/Local/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

> **✅ VERIFIED WORKING**: These paths are confirmed working as of 2025-07-20
> - Java: OpenJDK 21.0.6 from Android Studio JBR
> - ADB: Connected device `emulator-5554`
> - Build: Successful with proper JAVA_HOME setup

### Quick Setup Commands for Future Sessions

**🚀 Option 1: One-Line Setup (Fastest)**
```bash
cd "Documents/Claude-Projects/Ausgetrunken" && source project-docs/setup-env.sh && ./gradlew setupCheck
```

**🔧 Option 2: Use Setup Scripts from project-docs**
```bash
# For Unix/Git Bash (Recommended)
source project-docs/setup-env.sh

# For Windows Command Prompt  
project-docs/setup-env.bat
```

**⚡ Option 3: Use Gradle Wrapper with auto JAVA_HOME (RECOMMENDED)**
```bash
# Create a simple function for the session
gdw() { export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew "$@"; }

# Then use it like: gdw setupCheck, gdw assembleDebug, etc.
gdw setupCheck
gdw assembleDebug
gdw installDebug
gdw build    # ✅ VERIFIED: Builds successfully in 1m 38s
```

**📋 Single Command Method (Copy-Paste Ready)**
```bash
# For immediate use - just copy and paste this line:
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew build
```

**🛠️ Option 4: Manual Setup**
```bash
# Navigate to project directory
cd "Documents/Claude-Projects/Ausgetrunken"

# Set Java Home (required for Gradle)
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Verify setup
./gradlew setupCheck
```

### Build & Run Commands

**🎯 Using gdw function (Recommended - Auto JAVA_HOME)**
```bash
# Set up the function (run once per session)
gdw() { export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew "$@"; }

# Check environment and devices
gdw setupCheck
gdw checkDevices

# Build and install
gdw buildAndInstall

# Complete workflow: clean, build, install, and start
gdw quickStart

# Individual tasks
gdw clean
gdw assembleDebug
gdw installDebug
```

**📋 Custom Gradle Tasks**
```bash
# Environment verification (using gdw function)
gdw setupCheck    # Check JAVA_HOME, Android SDK, devices
gdw setJavaHome   # Show JAVA_HOME setup instructions

# Development workflow
gdw buildAndInstall  # Build + install debug APK
gdw quickStart      # Clean + build + install + start app
gdw checkDevices    # List connected Android devices
```

**⚙️ Standard Commands (Require JAVA_HOME)**
```bash
# Manual JAVA_HOME setup required for these
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"

# Build commands
./gradlew clean
./gradlew assembleDebug
./gradlew installDebug

# Utility commands
adb devices
adb shell am start -n com.ausgetrunken/.MainActivity
```

### Common Development Tasks
```bash
# Lint and type check
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew lint
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew compileDebugKotlin

# Run tests
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew test

# Generate APK for distribution
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew assembleRelease
```

## MCP (Model Context Protocol) Integration

### Server Setup
This project can be connected to MCP servers for enhanced functionality:

> 🚨 **CRITICAL**: Use the complete MCP configuration from `C:\Users\marko\Documents\Claude-Projects\MCP\FINAL-MCP-CONFIG.json`

1. **Complete MCP Configuration** (Required for full functionality):
   ```json
   {
     "mcpServers": {
       "android-studio": {
         "command": "node",
         "args": ["C:\\Users\\marko\\Documents\\Claude-Projects\\MCP\\android-studio-mcp-server\\dist\\index.js"],
         "env": {
           "ANDROID_HOME": "C:\\Users\\marko\\AppData\\Local\\Android\\Sdk",
           "ANDROID_SDK_ROOT": "C:\\Users\\marko\\AppData\\Local\\Android\\Sdk",
           "ANDROID_STUDIO_PATH": "C:\\Program Files\\Android\\Android Studio\\bin\\studio64.exe"
         }
       },
       "supabase": {
         "command": "cmd",
         "args": [
           "/c",
           "npx",
           "-y",
           "@supabase/mcp-server-supabase@latest",
           "--project-ref=xjlbypzhixeqvksxnilk"
         ],
         "env": {
           "SUPABASE_ACCESS_TOKEN": "sbp_299329bae09e3dbec01c9d9f560e7d56d8a2cded",
           "SUPABASE_ANON_KEY": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqbGJ5cHpoaXhlcXZrc3huaWxrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTI2OTQ4MjEsImV4cCI6MjA2ODI3MDgyMX0.PrcrF1pA4KB30VlOJm2MYkOLlgf3e3SPn2Uo_eiDKfc",
           "SUPABASE_SERVICE_ROLE_KEY": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhqbGJ5cHpoaXhlcXZrc3huaWxrIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc1MjY5NDgyMSwiZXhwIjoyMDY4MjcwODIxfQ.s3a9J-qrCmK6iZcceGC4JMXoQgQU31fpPGQC5z3up5A"
         }
       }
     }
   }
   ```

### Supabase Database Access Instructions

**✅ VERIFIED WORKING**: Direct database queries through Task tool agent using service role key.

**For Database Queries:**
1. Always use the Task tool with general-purpose agent for Supabase queries
2. The agent will use the service role key to access full database
3. Example query pattern:
   ```
   Task: Query Supabase database with SQL: SELECT * FROM user_profiles WHERE email = 'user@example.com'
   ```

**Common Database Operations:**
- **Find User**: `SELECT * FROM user_profiles WHERE email = 'email@example.com'`
- **Check Subscriptions**: `SELECT * FROM wineyard_subscriptions WHERE user_id = 'user-id-here'`
- **List Wineyards**: `SELECT * FROM wineyards WHERE is_active = true`
- **Count Records**: `SELECT COUNT(*) FROM table_name WHERE condition`

**Authentication Context:**
- **Service Role Key**: Full database access, bypasses RLS policies
- **Anon Key**: Limited access with Row Level Security enforcement
- **Access Token**: Admin API access for project management

### MCP Benefits for This Project
- **Direct Database Queries**: Real-time Supabase data access with SQL queries
- **Android Development**: Build, test, and deploy through MCP tools
- **File System Operations**: Project file management with proper permissions
- **Integration Testing**: End-to-end testing with database and device integration
- **Enhanced Debugging**: Database inspection and logging capabilities

## Startup Optimization Tips

### Quick Development Workflow
1. **Fastest Setup** (recommended for returning to project):
   ```bash
   cd "Documents/Claude-Projects/Ausgetrunken" && export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && ./gradlew quickStart
   ```

2. **Development Build & Test**:
   ```bash
   # Quick compilation check
   ./gradlew compileDebugKotlin
   
   # Full build when ready
   ./gradlew assembleDebug
   ```

3. **Session Preparation Checklist**:
   - Check `Development-History.md` for latest changes and session context
   - Review any TODO comments in recently modified files
   - Check git status for uncommitted work: `git status`
   - Verify emulator is running before builds: `./gradlew checkDevices`

### IDE & Tools Setup
1. **Android Studio**: Recommended for complex UI work and debugging
2. **Git Integration**: Use SSH authentication (already configured)
3. **Build Optimization**: Use `--parallel` flag for faster builds
4. **Debugging**: Check `Debugging-Logs-Manual` for runtime issues

### Project Context & Architecture
- **Current Focus**: Customer experience and authentication flows
- **User Types**: `CUSTOMER` (lands on discovery page) vs `WINEYARD_OWNER` (lands on profile)
- **Key Components**: Customer landing with pagination, sophisticated theming, logout functionality
- **Known Issues**: Registration error for new customers (investigate in next session)

## Troubleshooting

### Common Issues
1. **JAVA_HOME not set**: Always export JAVA_HOME before running Gradle commands
2. **No devices found**: Start Android Studio and launch an emulator first
3. **Build failures**: Run `./gradlew clean` then rebuild
4. **Permission issues**: Ensure Android SDK tools are in PATH
5. **Authentication Issues**: Check splash screen logs for user type detection
6. **Database Foreign Key Errors**: Ensure wineyards exist before creating wines
7. **Customer Registration**: Known issue - investigate error in registration flow

### Verification Commands
```bash
# Check Java installation
"$JAVA_HOME/bin/java" -version

# Check Android tools
"/c/Users/marko/AppData/Local/Android/Sdk/platform-tools/adb.exe" version

# Check Gradle wrapper
./gradlew --version

# List available Android build tools
ls "/c/Users/marko/AppData/Local/Android/Sdk/build-tools/"
```

## Git & GitHub Setup

### Repository Information
- **GitHub Repository**: https://github.com/Nonnsense90/Ausgetrunken
- **SSH Remote**: `git@github.com:Nonnsense90/Ausgetrunken.git`
- **GitHub Username**: Nonnsense90
- **User ID**: 9868679

### SSH Authentication Setup
```bash
# Generate SSH key (already done)
ssh-keygen -t ed25519 -C "marko.nonninger@gmail.com" -f ~/.ssh/id_ed25519 -N ""

# Test connection
ssh -T git@github.com

# Should return: "Hi Nonnsense90! You've successfully authenticated"
```

### Git Configuration
```bash
# Set user info for commits
git config user.name "Nonnsense90"
git config user.email "9868679+Nonnsense90@users.noreply.github.com"

# Check remote
git remote -v
```

### Common Git Commands
```bash
# Check status
git status

# Add all changes and commit
git add . && git commit -m "Description of changes"

# Push to GitHub
git push

# Pull latest changes
git pull origin master
```

### SSH Key Management
- **Public Key Location**: `~/.ssh/id_ed25519.pub`
- **Private Key Location**: `~/.ssh/id_ed25519`
- **Current Public Key**: `ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGK9m0x574tq5Qh98WPtS02+y83GuA3s/ek5TxOPOTtp marko.nonninger@gmail.com`

## Recent Development History

### Data Synchronization Architecture Fix ✅
**Status: ✅ Complete - 2025-07-26**

**CRITICAL LEARNING**: Major subscription sync issue resolved - this pattern should be applied project-wide.

**Problem Discovered:**
- Customer had 3 inactive subscriptions in Supabase
- Local database only stored active subscriptions (`AND isActive = 1` filter)
- App checked local first → found nothing → tried to create new subscription
- Supabase rejected due to existing inactive subscription → "duplicate key constraint" error
- UI showed "already subscribed" despite logic returning `false`

**Root Cause:**
- **Local-First Architecture**: App prioritized local database over Supabase source of truth
- **Incomplete Sync**: Local database didn't reflect complete Supabase state
- **Logic Mismatch**: Different queries for UI display vs subscription checks

**Solution Implemented:**
- **Enhanced Logging**: Detailed debugging throughout subscription flow
- **Fixed Sync Logic**: Clear local data before inserting fresh Supabase data  
- **Supabase-First Checks**: Check Supabase directly for ANY existing subscription
- **Reactivation Logic**: Reactivate inactive subscriptions instead of creating new ones

**Key Files Modified:**
- `CustomerLandingViewModel.kt`: Enhanced `loadSubscriptions()` with full sync
- `WineyardSubscriptionRepository.kt`: Fixed `subscribeToWineyard()` and `syncSubscriptionsFromSupabase()`
- `WineyardSubscriptionService.kt`: Enhanced `isSubscribed()` with detailed logging

**ARCHITECTURAL DECISION FOR FUTURE:**
> 🚨 **NEW DATA STRATEGY**: Always fetch from Supabase first, local database is backup only
> - **Primary Source**: Supabase (real-time, always current)
> - **Fallback**: Local database (offline/network failure only)
> - **Benefit**: Eliminates sync issues, ensures data consistency
> - **Implementation**: Update all repositories to follow remote-first pattern

### UX Improvements & Code Quality ✅
**Status: ✅ Complete - 2025-01-23**

**Navigation Enhancement:**
- **Wine Creation Flow**: Fixed navigation after creating wine to return to wineyard detail page instead of wine detail page
- **Improved User Experience**: Users now see their newly created wine in the wineyard's wine list, providing better context and workflow continuity
- **Navigation Event Refactoring**: Updated `AddWineViewModel` to use `NavigateBack` event for consistent navigation patterns

**Code Quality & Compilation Fixes:**
- **Type Safety Improvement**: Resolved compilation bug in `AuthenticatedRepository.kt` involving type incompatibility between `AuthenticationException` and `AppError`
- **Error Handling Optimization**: Removed redundant error mapping since `AppResult.catchingSuspend` already handles exception-to-AppError conversion automatically
- **Clean Architecture**: Simplified error handling flow while maintaining robust exception management

**Technical Details:**
- **File Modified**: `AddWineViewModel.kt:233` - Changed navigation event from `NavigateToWineDetail` to `NavigateBack`
- **File Modified**: `AuthenticatedRepository.kt:56-62` - Removed redundant `mapError` block that was causing type conflicts
- **Impact**: Clean compilation, better UX flow, and simplified error handling architecture

**Build Status**: ✅ All builds successful, no compilation warnings or errors

### Push Notification System 🎉
**Status: ✅ Complete - MAJOR MILESTONE ACHIEVED**

**BREAKTHROUGH**: End-to-end push notification system is now fully operational! Notifications successfully flow from wineyard owners to subscribed customers across devices.

**Core Achievement:**
- **Complete FCM Integration**: Firebase Cloud Messaging working from Android → Supabase → Customer devices
- **Real-time Notifications**: Low stock alerts, custom messages, and critical stock warnings delivered instantly
- **Cross-Device Functionality**: Owner (emulator) sends notifications, customer (physical device) receives them
- **Production-Ready**: All authentication, database, and delivery mechanisms working flawlessly

**Critical Issues Resolved:**
1. **Firebase Authentication Bug**: Fixed private key format conversion for Deno crypto API in Edge Functions
2. **Database Foreign Key Error**: Implemented proper sender_id resolution using wineyard owner lookup
3. **FCM Token Retrieval**: Enhanced token validation and storage mechanism
4. **Wineyard ID Mismatch**: Corrected notification routing to use wine's actual wineyard, not context wineyard

**Technical Implementation:**
- **Supabase Edge Function**: `send-fcm-notification` with proper error handling and logging
- **Android FCM Service**: `AusgetrunkenMessagingService` with comprehensive notification display
- **Token Management**: `FCMTokenManager` with automatic user session synchronization
- **Subscription System**: Real-time subscriber querying with notification type filtering

**Notification Flow Verified:**
1. ✅ Owner selects wine from notification management screen
2. ✅ System identifies wine's wineyard and finds active subscribers
3. ✅ FCM tokens retrieved from Supabase user profiles
4. ✅ Firebase authentication successful with service account
5. ✅ Push notifications delivered to customer devices
6. ✅ Delivery records stored for tracking and analytics

**Key Files Implemented/Fixed:**
- `supabase/functions/send-fcm-notification/index.ts` - Core notification delivery logic
- `NotificationManagementViewModel.kt` - Enhanced with proper wineyard ID handling
- `NotificationRepositoryImpl.kt` - Robust FCM integration with comprehensive logging
- `AusgetrunkenMessagingService.kt` - Production-ready notification display service
- `FCMTokenManager.kt` - Automatic token synchronization with user authentication

**Testing Results:**
- **Owner App**: Successfully shows "Sent notification to 1 subscribers" ✅
- **Customer Device**: Receives actual push notification with proper title/message ✅
- **Supabase Logs**: No errors, successful Firebase authentication and delivery ✅
- **Database**: Proper notification and delivery records created ✅

### Wine Management System
**Status: ✅ Complete**

Implemented comprehensive wine management functionality with full CRUD operations and modern UI patterns:

**Core Features:**
- **Complete Wine Management**: Add, view, edit, delete wines with 20 wine limit per wineyard
- **Navigation Integration**: Seamless navigation from wineyard details to wine management
- **Form Validation**: Comprehensive validation for wine creation/editing with real-time error feedback
- **Database Synchronization**: Hybrid approach using Supabase as primary data source with Room for offline support

**UI/UX Enhancements:**
- **Pull-to-Refresh**: Material 3 pull-to-refresh functionality for wine list updates
- **Real-time UI Updates**: Immediate wine deletion from UI after successful database removal
- **Auto-refresh**: Automatic wine list refresh when returning from add/edit screens
- **Loading States**: Progress indicators during wine operations with smooth animations
- **Responsive Design**: Proper loading states, error handling, and user feedback

### Previous Features
- **Authentication System**: Supabase-based auth with persistent login and user type management
- **Profile Management**: User profile screens with wineyard owner functionality
- **Wineyard Management**: Full CRUD operations for wineyards with location support
- **Subscription System**: Customer subscription to wineyards with notification preferences
- **Navigation Framework**: Type-safe navigation using Navigation Compose

## Project Structure Notes
- Main source: `app/src/main/java/com/ausgetrunken/`
- Resources: `app/src/main/res/`
- Manifest: `app/src/main/AndroidManifest.xml`
- Build config: `app/build.gradle.kts` and `build.gradle.kts`
- Dependencies: `gradle/libs.versions.toml`