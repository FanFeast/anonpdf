#!/bin/sh
# Writes /app/bin/anonpdf: starts Java directly, with the classpath, options and
# main class the Java packager's launcher reads from AnonPDF.cfg.
#
# That native launcher forks a helper to look for a package manager, and in the
# Flatpak runtime the helper aborts on its way out ("pure virtual method
# called", and a core dump on every start). The app runs regardless, but it
# needs no launcher at all here, so this skips it.
#
#   make-launcher.sh <installed app dir> > /app/bin/anonpdf
set -eu

app=$1
cfg=$app/lib/app/AnonPDF.cfg
appdir=$app/lib/app

value() { sed -n "s/^$1=//p" "$cfg" | sed "s#\\\$APPDIR#$appdir#g"; }

main=$(value app.mainclass)
classpath=$(value app.classpath | paste -sd: -)
options=$(value java-options | sed "s/'/'\\\\''/g; s/.*/'&'/" | paste -sd' ' -)

[ -n "$main" ] && [ -n "$classpath" ] || { echo "Nothing to launch in $cfg" >&2; exit 1; }

cat <<EOF
#!/bin/sh
exec $app/lib/runtime/bin/java $options -cp '$classpath' $main "\$@"
EOF
