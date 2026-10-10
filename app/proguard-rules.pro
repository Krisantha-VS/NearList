# R8 rules for the release build (isMinifyEnabled = true, isShrinkResources = true).
#
# Most libraries ship their own consumer rules (Room, Lifecycle, Fragment, Play services,
# Material). The rules below make the app's own reflection points explicit, so a library
# update that drops a consumer rule doesn't break the release build silently.

# Readable stack traces from release builds (mapping.txt is in app/build/outputs/mapping/release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room -------------------------------------------------------------------
# Room finds the generated AppDatabase_Impl by name with reflection.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.sharma.nearlist.data.db.AppDatabase_Impl { *; }
# Entities: column names come from field names; keep them stable.
-keep class com.sharma.nearlist.data.entity.** { *; }
-keep @androidx.room.Dao interface com.sharma.nearlist.data.dao.** { *; }
# Category is stored by name (Converters uses Category.key() / fromKey()).
-keepclassmembers enum com.sharma.nearlist.data.model.Category { *; }

# --- ViewModels -------------------------------------------------------------
# The default ViewModelProvider factory creates these by reflection
# (AndroidViewModel subclasses through their (Application) constructor).
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>();
}

# --- Fragments --------------------------------------------------------------
# FragmentFactory re-creates fragments by class name after process death.
-keep public class * extends androidx.fragment.app.Fragment { public <init>(); }

# --- Google Play services (Location / Geofencing) ---------------------------
# play-services-location ships consumer rules; these cover parcelables used in the
# geofence PendingIntent extras.
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**
