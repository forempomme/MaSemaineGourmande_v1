#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
# Licensed under the Apache License, Version 2.0
#

##############################################################################
# Shell script for running Gradle on UN*X
##############################################################################

# Tentative de définir APP_HOME
APP_HOME=$( cd "${0%/*}/.." && pwd -P ) 2>/dev/null || APP_HOME=.

APP_NAME="Gradle"
APP_BASE_NAME="${0##*/}"

# Options JVM par défaut — sans guillemets internes problématiques
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

# Utilisation maximale des file descriptors si possible
MAX_FD=maximum

warn() {
    echo "$*"
} >&2

die() {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# Détection de l'OS
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
    CYGWIN* )   cygwin=true  ;;     #(
    Darwin* )   darwin=true  ;;     #(
    MSYS* | MINGW* ) msys=true ;;  #(
    NONSTOP* )  nonstop=true ;;
esac

CLASSPATH="${APP_HOME}/gradle/wrapper/gradle-wrapper.jar"

# Déterminer la commande Java
if [ -n "${JAVA_HOME}" ] ; then
    if [ -x "${JAVA_HOME}/jre/sh/java" ] ; then
        JAVACMD="${JAVA_HOME}/jre/sh/java"
    else
        JAVACMD="${JAVA_HOME}/bin/java"
    fi
    if [ ! -x "${JAVACMD}" ] ; then
        die "JAVA_HOME pointe vers un répertoire invalide : ${JAVA_HOME}
Vérifiez la variable JAVA_HOME."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "JAVA_HOME n'est pas défini et aucune commande 'java' n'est disponible dans PATH."
    fi
fi

# Accroître les file descriptors si possible
if ! "${cygwin}" && ! "${darwin}" && ! "${nonstop}" ; then
    case ${MAX_FD} in                                #(
        max*)
            MAX_FD=$( ulimit -H -n ) ||
                warn "Impossible de récupérer la limite maximale des FD."
    esac
    case ${MAX_FD} in                                #(
        '' | soft) :;;                               #(
        *)
            ulimit -n "${MAX_FD}" ||
                warn "Impossible de fixer la limite des FD à ${MAX_FD}."
    esac
fi

# Collecter tous les arguments
collect_args() {
    while [ "$#" -gt 0 ] ; do
        ARGS="${ARGS} $1"
        shift
    done
}

# Préparer les options de la JVM (gestion correcte des espaces)
set -- \
    "-Dorg.gradle.appname=${APP_BASE_NAME}" \
    -classpath "${CLASSPATH}" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"

# Sous Cygwin ou MSYS, convertir les chemins
if "${cygwin}" || "${msys}" ; then
    APP_HOME=$( cygpath --path --mixed "${APP_HOME}" )
    CLASSPATH=$( cygpath --path --mixed "${CLASSPATH}" )
    JAVACMD=$( cygpath --unix "${JAVACMD}" )
fi

exec "${JAVACMD}" ${DEFAULT_JVM_OPTS} ${JAVA_OPTS} ${GRADLE_OPTS} "$@"