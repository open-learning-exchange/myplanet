# myPlanet UI Guidelines

**Version**: 1.0
**Last Updated**: 2026-01-09

This document establishes consistent UI patterns, Material Design implementation standards, and accessibility requirements for the myPlanet Android application.

---

## Table of Contents

1. [Design Foundation](#design-foundation)
2. [Color System](#color-system)
3. [Typography](#typography)
4. [Spacing System](#spacing-system)
5. [Component Library](#component-library)
6. [Layout Patterns](#layout-patterns)
7. [Day/Night Theming](#daynight-theming)
8. [Accessibility](#accessibility)
9. [Responsive Design](#responsive-design)
10. [Anti-Patterns](#anti-patterns)

---

## Design Foundation

### Core Principles

| Principle | Description |
|-----------|-------------|
| **Material Design 3** | Follow Google's Material Design guidelines for consistency |
| **Offline-First Visual Feedback** | Clear indicators for sync status and offline availability |
| **Educational Focus** | Prioritize content readability and learning experience |
| **Accessibility** | WCAG AA compliance for inclusive design |
| **Day/Night Support** | Full theme support for user preference |

### Design System Hierarchy

```
res/values/
├── colors.xml          # Color definitions and semantic aliases
├── colors_night.xml    # Night mode overrides
├── dimens.xml          # Spacing and sizing tokens
├── styles.xml          # Component styles and themes
├── themes.xml          # Application themes
└── strings.xml         # Text resources
```

---

## Color System

### Primary Palette

```xml
<!-- Primary Brand Colors -->
<color name="colorPrimary">@color/md_blue_700</color>      <!-- #1976D2 -->
<color name="colorPrimaryDark">@color/md_blue_900</color>  <!-- #0D47A1 -->
<color name="colorPrimaryLight">@color/md_blue_500</color> <!-- #2196F3 -->
<color name="colorAccent">@color/md_red_500</color>        <!-- #F44336 -->
```

### Semantic Colors

Always use semantic color names rather than raw values:

```xml
<!-- Text Colors -->
<color name="daynight_textColor">@color/md_black_1000</color>  <!-- Primary text -->
<color name="hint_color">#808080</color>                        <!-- Secondary/hint text -->

<!-- Background Colors -->
<color name="secondary_bg">#FFFFFF</color>                      <!-- Surface background -->
<color name="card_bg">#FFFFFF</color>                           <!-- Card background -->
<color name="daynight_grey">@color/md_blue_grey_50</color>     <!-- Divider/border -->

<!-- Status Colors -->
<color name="success_color">@color/md_green_500</color>
<color name="warning_color">@color/md_orange_500</color>
<color name="error_color">@color/md_red_500</color>
```

### Color Usage in Layouts

```xml
<!-- CORRECT: Use color resources -->
<TextView
    android:textColor="@color/daynight_textColor"
    android:background="@color/secondary_bg" />

<!-- CORRECT: Use theme attributes for dynamic theming -->
<TextView
    android:textColor="?attr/colorOnSurface"
    android:background="?attr/colorSurface" />

<!-- AVOID: Hardcoded colors -->
<TextView
    android:textColor="#000000"
    android:background="#FFFFFF" />
```

### Color in Kotlin

```kotlin
// CORRECT: Use ContextCompat for compatibility
val primaryColor = ContextCompat.getColor(context, R.color.colorPrimary)

// CORRECT: Resolve theme attributes
val typedValue = TypedValue()
context.theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
val textColor = typedValue.data

// AVOID: Direct color parsing
val color = Color.parseColor("#1976D2")
```

---

## Typography

### Type Scale

| Style | Size | Weight | Use Case |
|-------|------|--------|----------|
| Headline | 24sp | Bold | Screen titles, section headers |
| Title | 18sp | Bold | Card titles, list headers |
| Body Large | 16sp | Normal | Primary content |
| Body | 14sp | Normal | Secondary content |
| Caption | 12sp | Normal | Labels, timestamps |

### Text Styles

```xml
<!-- styles.xml -->
<style name="HeaderText">
    <item name="android:textStyle">bold</item>
    <item name="android:padding">@dimen/padding_normal</item>
    <item name="android:textColor">@color/daynight_textColor</item>
    <item name="android:textSize">@dimen/text_size_mid</item>
</style>

<style name="Header">
    <item name="android:layout_width">wrap_content</item>
    <item name="android:layout_gravity">center</item>
    <item name="android:gravity">center</item>
    <item name="android:padding">@dimen/padding_normal</item>
    <item name="android:textStyle">bold</item>
    <item name="android:textSize">24sp</item>
    <item name="android:textColor">@color/daynight_textColor</item>
    <item name="android:layout_height">wrap_content</item>
</style>
```

### Text Size Resources

```xml
<!-- dimens.xml -->
<dimen name="text_size_large">18sp</dimen>   <!-- Titles -->
<dimen name="text_size_mid">16sp</dimen>     <!-- Body -->
<dimen name="text_size_small">14sp</dimen>   <!-- Secondary -->
```

### Usage in Layouts

```xml
<!-- Apply text styles -->
<TextView
    style="@style/HeaderText"
    android:text="@string/section_title" />

<!-- Or use dimensions directly -->
<TextView
    android:textSize="@dimen/text_size_mid"
    android:textColor="@color/daynight_textColor" />
```

---

## Spacing System

### Spacing Tokens

```xml
<!-- dimens.xml - Consistent spacing scale -->
<dimen name="padding_small">4dp</dimen>       <!-- Tight spacing -->
<dimen name="padding_normal">8dp</dimen>      <!-- Default spacing -->
<dimen name="padding_large">16dp</dimen>      <!-- Comfortable spacing -->
<dimen name="padding_very_large">24dp</dimen> <!-- Section spacing -->

<!-- Common fixed sizes -->
<dimen name="_1dp">1dp</dimen>   <!-- Dividers, borders -->
<dimen name="_2dp">2dp</dimen>   <!-- Minimal spacing -->
<dimen name="_4dp">4dp</dimen>   <!-- Icon padding -->
<dimen name="_10dp">10dp</dimen> <!-- Form field spacing -->
<dimen name="_20dp">20dp</dimen> <!-- Card margins -->
<dimen name="_24dp">24dp</dimen> <!-- Content padding -->
```

### Icon Sizes

```xml
<dimen name="menu_item_icon_size">24dp</dimen>   <!-- Toolbar icons -->
<dimen name="icon_size_mid">36dp</dimen>          <!-- List icons -->
<dimen name="image_size_home_card">40dp</dimen>   <!-- Dashboard icons -->
```

### Spacing Usage

```xml
<!-- CORRECT: Use dimension resources -->
<LinearLayout
    android:padding="@dimen/padding_large"
    android:layout_margin="@dimen/padding_normal">

    <TextView
        android:layout_marginBottom="@dimen/padding_normal" />

</LinearLayout>

<!-- AVOID: Hardcoded dimensions -->
<LinearLayout
    android:padding="16dp"
    android:layout_margin="8dp">
```

---

## Component Library

### Buttons

#### Button Styles

```xml
<!-- Primary action button -->
<style name="PrimaryButton" parent="Theme.AppCompat">
    <item name="colorButtonNormal">@color/mainColor</item>
    <item name="colorControlHighlight">@color/colorAccent</item>
</style>

<!-- Accent button (highest priority action) -->
<style name="AccentButton" parent="Theme.AppCompat">
    <item name="android:textColor">@color/md_white_1000</item>
    <item name="colorButtonNormal">@color/mainColor</item>
    <item name="colorControlHighlight">@color/colorPrimary</item>
</style>

<!-- Flat/text button -->
<style name="PrimaryFlatButton" parent="Theme.AppCompat.Light">
    <item name="android:buttonStyle">@style/Widget.AppCompat.Button.Borderless</item>
    <item name="colorControlHighlight">@color/colorAccent</item>
    <item name="colorAccent">@color/colorPrimary</item>
</style>
```

#### Button Usage

```xml
<!-- Primary action -->
<Button
    android:id="@+id/btn_save"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:theme="@style/PrimaryButton"
    android:text="@string/save" />

<!-- Material Button (preferred) -->
<com.google.android.material.button.MaterialButton
    android:id="@+id/btn_enroll"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    app:backgroundTint="@color/colorPrimary"
    android:text="@string/enroll" />

<!-- Text button for secondary actions -->
<com.google.android.material.button.MaterialButton
    style="@style/Widget.MaterialComponents.Button.TextButton"
    android:text="@string/cancel" />
```

### Cards

```xml
<!-- Standard card -->
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_margin="@dimen/padding_normal"
    app:cardBackgroundColor="@color/card_bg"
    app:cardCornerRadius="@dimen/padding_normal"
    app:cardElevation="@dimen/_2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="@dimen/padding_large">

        <!-- Card content -->

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### Dialogs

```xml
<!-- Dialog theme -->
<style name="CustomAlertDialog" parent="ThemeOverlay.MaterialComponents.Dialog.Alert">
    <item name="android:background">@color/daynight_grey</item>
    <item name="android:textColorPrimary">@color/daynight_textColor</item>
    <item name="android:textColor">@color/daynight_textColor</item>
    <item name="buttonBarNegativeButtonStyle">@style/NegativeButtonStyle</item>
    <item name="buttonBarPositiveButtonStyle">@style/NegativeButtonStyle</item>
</style>

<style name="NegativeButtonStyle" parent="Widget.MaterialComponents.Button.TextButton.Dialog">
    <item name="android:textColor">@color/colorAccent</item>
</style>
```

```kotlin
// Show styled dialog
MaterialAlertDialogBuilder(context, R.style.CustomAlertDialog)
    .setTitle(R.string.dialog_title)
    .setMessage(R.string.dialog_message)
    .setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss() }
    .setNegativeButton(R.string.cancel, null)
    .show()
```

### Input Fields

```xml
<!-- Text input with Material styling -->
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/email_hint"
    style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/et_email"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textEmailAddress" />

</com.google.android.material.textfield.TextInputLayout>
```

### List Items

```xml
<!-- row_course.xml - Standard list item -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="@dimen/padding_large"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical">

    <ImageView
        android:id="@+id/iv_course_icon"
        android:layout_width="@dimen/icon_size_mid"
        android:layout_height="@dimen/icon_size_mid"
        android:contentDescription="@string/course_icon" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical"
        android:layout_marginStart="@dimen/padding_large">

        <TextView
            android:id="@+id/tv_course_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="@dimen/text_size_mid"
            android:textColor="@color/daynight_textColor"
            android:ellipsize="end"
            android:maxLines="2" />

        <TextView
            android:id="@+id/tv_course_description"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="@dimen/text_size_small"
            android:textColor="@color/hint_color"
            android:ellipsize="end"
            android:maxLines="1"
            android:layout_marginTop="@dimen/padding_small" />

    </LinearLayout>

    <ImageButton
        android:id="@+id/btn_options"
        android:layout_width="@dimen/_40dp"
        android:layout_height="@dimen/_40dp"
        android:src="@drawable/ic_more_vert"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@string/more_options" />

</LinearLayout>
```

### Progress Indicators

```xml
<!-- Circular progress (indeterminate) -->
<com.google.android.material.progressindicator.CircularProgressIndicator
    android:id="@+id/progress"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:indeterminate="true" />

<!-- Linear progress (determinate) -->
<com.google.android.material.progressindicator.LinearProgressIndicator
    android:id="@+id/progress_download"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:max="100"
    app:trackThickness="4dp" />
```

### Bottom Navigation

```xml
<com.google.android.material.bottomnavigation.BottomNavigationView
    android:id="@+id/bottom_navigation"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom"
    app:menu="@menu/bottom_nav_menu"
    app:labelVisibilityMode="labeled" />
```

---

## Layout Patterns

### Standard Screen Structure

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/screen_title" />

    </com.google.android.material.appbar.AppBarLayout>

    <!-- Main content -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="@dimen/padding_large">

            <!-- Content here -->

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_add"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="@dimen/padding_large"
        android:src="@drawable/ic_add"
        app:backgroundTint="@color/colorAccent"
        android:contentDescription="@string/add_item" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### List Screen Pattern

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_items"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:clipToPadding="false"
        android:padding="@dimen/padding_normal" />

    <!-- Empty state -->
    <LinearLayout
        android:id="@+id/empty_state"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:gravity="center"
        android:orientation="vertical"
        android:visibility="gone"
        android:padding="@dimen/padding_very_large">

        <ImageView
            android:layout_width="@dimen/_80dp"
            android:layout_height="@dimen/_80dp"
            android:src="@drawable/ic_empty_list"
            android:alpha="0.5"
            android:contentDescription="@null" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/no_items_found"
            android:textSize="@dimen/text_size_mid"
            android:textColor="@color/hint_color"
            android:layout_marginTop="@dimen/padding_large" />

    </LinearLayout>

    <!-- Loading state -->
    <ProgressBar
        android:id="@+id/progress"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:visibility="gone" />

</FrameLayout>
```

### Form Layout Pattern

```xml
<ScrollView
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="@dimen/padding_large">

        <com.google.android.material.textfield.TextInputLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/field_name"
            style="@style/Widget.MaterialComponents.TextInputLayout.OutlinedBox">

            <com.google.android.material.textfield.TextInputEditText
                android:id="@+id/et_name"
                android:layout_width="match_parent"
                android:layout_height="wrap_content" />

        </com.google.android.material.textfield.TextInputLayout>

        <!-- Add spacing between fields -->
        <Space
            android:layout_width="match_parent"
            android:layout_height="@dimen/padding_large" />

        <!-- More fields... -->

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btn_submit"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/padding_very_large"
            android:text="@string/submit" />

    </LinearLayout>
</ScrollView>
```

---

## Day/Night Theming

### Theme Configuration

```xml
<!-- values/themes.xml -->
<style name="AppTheme" parent="Theme.MaterialComponents.DayNight.NoActionBar">
    <item name="colorPrimary">@color/colorPrimary</item>
    <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
    <item name="colorAccent">@color/colorAccent</item>
    <item name="android:statusBarColor">@color/colorPrimaryDark</item>
</style>
```

### Night Mode Colors

```xml
<!-- values-night/colors.xml -->
<resources>
    <color name="daynight_textColor">@color/md_white_1000</color>
    <color name="daynight_grey">@color/md_grey_900</color>
    <color name="daynight_white_grey">#1E1E1E</color>
    <color name="card_bg">#2D2D2D</color>
    <color name="secondary_bg">#1E1E1E</color>
    <color name="light_dark">#121212</color>
</resources>
```

### Theme-Aware Resources

```xml
<!-- Use daynight_ prefix for theme-variant colors -->
<TextView
    android:textColor="@color/daynight_textColor"
    android:background="@color/secondary_bg" />

<!-- Or use theme attributes -->
<TextView
    android:textColor="?attr/colorOnSurface"
    android:background="?attr/colorSurface" />
```

### Programmatic Theme Handling

```kotlin
// Check current theme
val isNightMode = resources.configuration.uiMode and
    Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

// Apply theme preference
AppCompatDelegate.setDefaultNightMode(
    when (preference) {
        "light" -> AppCompatDelegate.MODE_NIGHT_NO
        "dark" -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
)
```

---

## Accessibility

### Content Descriptions

```xml
<!-- REQUIRED: All interactive elements need descriptions -->
<ImageButton
    android:src="@drawable/ic_download"
    android:contentDescription="@string/download_resource" />

<!-- Decorative images can be null -->
<ImageView
    android:src="@drawable/ic_decorative"
    android:contentDescription="@null"
    android:importantForAccessibility="no" />
```

### Touch Targets

```xml
<!-- Minimum touch target: 48dp x 48dp -->
<ImageButton
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:padding="12dp"
    android:src="@drawable/ic_close_24" />
```

### Color Contrast

| Element | Minimum Contrast | Target |
|---------|------------------|--------|
| Normal text | 4.5:1 | 7:1 |
| Large text (18sp+) | 3:1 | 4.5:1 |
| UI components | 3:1 | 4.5:1 |

### Focus Management

```kotlin
// Set initial focus for dialogs
alertDialog.setOnShowListener {
    binding.etInput.requestFocus()
}

// Navigate focus programmatically
binding.etEmail.setOnEditorActionListener { _, actionId, _ ->
    if (actionId == EditorInfo.IME_ACTION_NEXT) {
        binding.etPassword.requestFocus()
        true
    } else false
}
```

### Screen Reader Support

```kotlin
// Announce changes
binding.tvStatus.announceForAccessibility(getString(R.string.download_complete))

// Group related content
binding.cardView.accessibilityDelegate = object : View.AccessibilityDelegate() {
    override fun onInitializeAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfo
    ) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.text = "${course.title}. ${course.description}"
    }
}
```

---

## Responsive Design

### Screen Size Breakpoints

| Category | Width | Layout Approach |
|----------|-------|-----------------|
| Phone | < 600dp | Single column |
| Tablet (portrait) | 600-840dp | Master-detail optional |
| Tablet (landscape) | > 840dp | Master-detail |

### Resource Qualifiers

```
res/
├── layout/              # Default (phone)
├── layout-sw600dp/      # Tablet portrait
├── layout-sw840dp/      # Tablet landscape
├── values/              # Default dimensions
├── values-sw600dp/      # Tablet dimensions
└── values-sw840dp/      # Large tablet dimensions
```

### Adaptive Layouts

```xml
<!-- layout/fragment_courses.xml (phone) -->
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/rv_courses"
    app:layoutManager="LinearLayoutManager" />

<!-- layout-sw600dp/fragment_courses.xml (tablet) -->
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/rv_courses"
    app:layoutManager="GridLayoutManager"
    app:spanCount="2" />
```

### Flexible Dimensions

```xml
<!-- values/dimens.xml -->
<dimen name="content_padding">16dp</dimen>
<dimen name="card_width">match_parent</dimen>

<!-- values-sw600dp/dimens.xml -->
<dimen name="content_padding">32dp</dimen>
<dimen name="card_width">320dp</dimen>
```

---

## Anti-Patterns

### Prohibited Practices

```xml
<!-- NEVER: Inline styles -->
<TextView
    android:textSize="16sp"
    android:textColor="#000000"
    android:padding="8dp" />

<!-- NEVER: Hardcoded strings -->
<Button android:text="Submit" />

<!-- NEVER: Missing content descriptions -->
<ImageButton android:src="@drawable/ic_menu" />

<!-- NEVER: Fixed dimensions for content -->
<TextView
    android:layout_width="200dp"
    android:layout_height="50dp" />
```

### Correct Approaches

```xml
<!-- CORRECT: Use resources and styles -->
<TextView
    style="@style/Body"
    android:textSize="@dimen/text_size_mid"
    android:textColor="@color/daynight_textColor"
    android:padding="@dimen/padding_normal" />

<!-- CORRECT: String resources -->
<Button android:text="@string/submit" />

<!-- CORRECT: Content descriptions -->
<ImageButton
    android:src="@drawable/ic_menu"
    android:contentDescription="@string/open_menu" />

<!-- CORRECT: Flexible dimensions -->
<TextView
    android:layout_width="wrap_content"
    android:layout_height="wrap_content" />
```

### Kotlin UI Anti-Patterns

```kotlin
// NEVER: Style manipulation in code
view.setBackgroundColor(Color.WHITE)
textView.setTextColor(Color.parseColor("#1976D2"))
button.setPadding(16, 16, 16, 16)

// CORRECT: Use resources
view.setBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
textView.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
button.setPadding(
    resources.getDimensionPixelSize(R.dimen.padding_large),
    resources.getDimensionPixelSize(R.dimen.padding_large),
    resources.getDimensionPixelSize(R.dimen.padding_large),
    resources.getDimensionPixelSize(R.dimen.padding_large)
)
```

---

## Review Checklist

Before merging UI changes, verify:

- [ ] All strings use `@string/` resources
- [ ] All dimensions use `@dimen/` resources
- [ ] All colors use `@color/` resources or theme attributes
- [ ] No inline `style=""` attributes with hardcoded values
- [ ] Interactive elements have content descriptions
- [ ] Touch targets are at least 48dp
- [ ] Layout works in both day and night themes
- [ ] Layout adapts to different screen sizes
- [ ] Text is readable (proper contrast)
- [ ] Empty/loading/error states are handled

---

## References

- [Material Design 3](https://m3.material.io/)
- [Android Design Guidelines](https://developer.android.com/design)
- [Web Content Accessibility Guidelines (WCAG) 2.1](https://www.w3.org/WAI/WCAG21/quickref/)
- [CODE_STYLE_GUIDE.md](CODE_STYLE_GUIDE.md) - Kotlin coding conventions
- [CLAUDE.md](../CLAUDE.md) - Project overview
