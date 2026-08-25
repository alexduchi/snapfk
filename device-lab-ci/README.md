# Device Lab v4.0

Application Android native Java, sans Gradle et sans dépendances externes.

Build officiel prévu : `javac -> d8 -> aapt2 -> zipalign -> apksigner` avec Android Platform 35 / Build Tools 35.0.1.

Fonctions : capteurs temps réel + graphiques/statistiques, niveau à bulle, GPS/GNSS, batterie, RAM/stockage/CPU/écran, réseau/Wi-Fi/mobile, Bluetooth/NFC/USB/caméras/audio, tests vibration/haut-parleur/micro/écran/lampe.

L'application ne déclare aucune permission INTERNET. Les permissions sensibles (localisation, micro, caméra, Bluetooth) sont demandées uniquement lors d'une action qui les nécessite.
