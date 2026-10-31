# Snow Screen R8 rules.
# The app has no reflection, JSON models, JNI or Class.forName usage, so no app-package keeps are needed:
#  - MainActivity / SnowService are kept automatically by AAPT-generated manifest rules.
#  - WeatherMode enum is only used via entries/name (no valueOf), safe to rename.
#  - Firebase and Play Billing ship their own consumer rules.

# Keep line numbers in stack traces; mapping.txt (upload to Play Console) restores original names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
