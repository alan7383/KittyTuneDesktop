if [ -z "${ALSA_PLUGIN_DIR:-}" ]; then
    export ALSA_PLUGIN_DIR="@alsaPlugins@"
fi
if [ -e /mnt/wslg/PulseServer ]; then
    if [ -z "${PULSE_SERVER:-}" ]; then
        export PULSE_SERVER=unix:/mnt/wslg/PulseServer
    fi
    if [ -z "${ALSA_CONFIG_PATH:-}" ] && [ ! -f /etc/asound.conf ] &&
       [ ! -f "$HOME/.asoundrc" ] && [ -f "@alsaConfig@" ]; then
        export ALSA_CONFIG_PATH="@alsaConfig@"
    fi
fi
