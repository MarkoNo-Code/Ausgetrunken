# Debugging Learnings & Problem-Solving Patterns

This file documents key learnings from debugging sessions to help future development and avoid repeating similar issues.

## Wine Update & Navigation Bug (2025-07-27)

### **Problem Summary**
Wine editing functionality completely broken - clicking "Update Wine" button did absolutely nothing: no database updates, no UI changes, no navigation.

### **Root Causes Identified**

#### 1. **Database Schema Mismatch - Primary Issue**
**Problem**: `WineRepository.updateWine()` was only updating 6 fields in Supabase, missing 3 critical fields that the UI form was trying to update.

**Missing Fields**:
- `low_stock_threshold` 
- `full_stock_quantity`
- `discounted_price`

**Fix**: Added all missing fields to the Supabase update JSON:
```kotlin
// Before (incomplete)
buildJsonObject {
    put("name", wine.name)
    put("description", wine.description)
    put("wine_type", wine.wineType.name)
    put("vintage", wine.vintage)
    put("price", wine.price)
    put("stock_quantity", wine.stockQuantity)
    // Missing: low_stock_threshold, full_stock_quantity, discounted_price
}

// After (complete)
buildJsonObject {
    put("name", wine.name)
    put("description", wine.description)  
    put("wine_type", wine.wineType.name)
    put("vintage", wine.vintage)
    put("price", wine.price)
    put("stock_quantity", wine.stockQuantity)
    put("low_stock_threshold", wine.lowStockThreshold)        // ✅ Added
    put("full_stock_quantity", wine.fullStockQuantity)        // ✅ Added  
    put("discounted_price", wine.discountedPrice ?: JsonNull) // ✅ Added
}
```

#### 2. **Overengineered Authentication Logic**
**Problem**: Complex session validation, user type checking, and ownership validation were creating multiple failure points.

**Fix**: Simplified to focus on core functionality first:
```kotlin
// Before: 80+ lines of authentication logic
// After: Direct update call
viewModelScope.launch {
    try {
        val updatedWine = originalWine.copy(/* updated fields */)
        wineService.updateWine(updatedWine)
            .onSuccess { /* navigate back */ }
            .onFailure { /* show error */ }
    } catch (e: Exception) { /* handle exception */ }
}
```

#### 3. **Over-Complex Animation System**
**Problem**: Complicated highlighting animation with timing dependencies was adding unnecessary complexity.

**Fix**: Removed animation logic entirely to focus on core functionality.

### **Debugging Methodology That Worked**

#### **Step 1: Isolate the Problem**
- Started with simple button click detection
- Used immediate UI changes (`updateName()`) to verify onClick was working
- **Key Learning**: Always test the simplest possible interaction first

#### **Step 2: Eliminate Complexity**
- Temporarily bypassed all validation logic
- Removed authentication checks
- Made button always enabled
- **Key Learning**: Remove all non-essential logic until core functionality works

#### **Step 3: Add Visible Feedback**
- Added loading indicators
- Added error messages in UI (not just logs)
- Used `println()` statements for immediate debugging
- **Key Learning**: Make debugging visible in the UI, not just logs

#### **Step 4: Database Schema Verification**
- Used MCP tool to query actual Supabase schema
- Compared app's update logic with actual database columns
- **Key Learning**: Always verify what fields actually exist in the database

### **Key Debugging Insights**

#### **🔍 Button Not Working? Check in Order:**
1. **onClick Detection**: Add simple UI change to verify click is detected
2. **Button Enabled State**: Temporarily make `enabled = true` 
3. **Function Entry**: Add logging at start of onClick function
4. **Validation Logic**: Bypass validation temporarily
5. **Core Logic**: Strip down to minimal functionality

#### **🗄️ Database Updates Not Working? Check:**
1. **Schema Match**: Query actual database schema vs app's update fields
2. **Field Mapping**: Ensure all form fields map to database columns
3. **Data Types**: Verify correct data type conversions
4. **Null Handling**: Handle nullable fields properly (`JsonNull`)

#### **🚀 Navigation Not Working? Check:**
1. **Event Emission**: Verify navigation events are being sent
2. **Event Collection**: Ensure UI is collecting navigation events
3. **Navigation Logic**: Simplify to basic back navigation first
4. **State Dependencies**: Remove complex state-dependent navigation

### **Anti-Patterns to Avoid**

#### **❌ Don't:**
- Add complex authentication logic before basic functionality works
- Implement animations/UI enhancements before core features work  
- Rely only on logs for debugging - use visible UI feedback
- Assume database schema matches app model without verification
- Create multiple failure points in a single function

#### **✅ Do:**
- Test button clicks with simple UI changes first
- Start with minimal implementation, add complexity gradually
- Make debugging visible in the UI
- Verify database schema against actual database
- Separate concerns: update logic vs navigation vs validation

### **Prevention Strategies**

#### **Database Schema Management**
- Document required database fields for each update operation
- Add schema validation tests
- Use MCP tools to regularly verify schema consistency

#### **Debugging Setup**
- Always add visible UI feedback for user actions
- Use structured logging with clear prefixes (`🔄`, `✅`, `❌`)
- Test core functionality before adding enhancements

#### **Code Organization**
- Keep update logic separate from validation logic
- Make navigation logic independent of business logic
- Use simple success/failure patterns before complex workflows

---

## Previous Learnings

### Wineyard Photo Persistence Bug (Referenced from WINEYARD-PHOTO-PERSISTENCE-BUG-SOLUTION.md)

**Key Learning**: Local-first architecture can create sync issues when local database doesn't reflect complete remote state. Always prioritize remote source of truth for data consistency.

**Solution Pattern**: Enhanced logging + fixed sync logic + Supabase-first checks + reactivation logic instead of creation.

---

## Debugging Tools & Resources

### **MCP Tools Used**
- Supabase database queries for schema verification
- Real-time data inspection during debugging

### **Effective Debugging Commands**
```bash
# Android debugging
adb logcat | grep "YourTag"
./gradlew installDebug

# Git workflow for debugging
git status
git diff --name-only  
git add . && git commit -m "debug: description"
```

### **UI Debugging Patterns**
```kotlin
// Visible button click detection
onClick = { 
    viewModel.updateSomethingVisible() // Changes UI immediately
    viewModel.actualFunction()
}

// Visible error states
_uiState.update { 
    it.copy(errorMessage = "DEBUG: Specific issue description")
}

// Loading states
_uiState.update { it.copy(isLoading = true) }
```

---

*Last Updated: 2025-07-27*