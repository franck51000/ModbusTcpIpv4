# ModbusTcpIpv4

Application Android de communication **Modbus TCP/IP** permettant de lire et écrire des registres Modbus sur des appareils compatibles via le protocole TCP/IPv4.

---

## 📱 Captures d'écran

L'interface utilisateur comprend :
- Section **Connexion** (IP, Port, Unit ID, bouton coloré)
- Section **Paramètres Modbus** (adresse, nombre de registres, sélecteur de fonction)
- Boutons **Lire** et **Écrire**
- **Mode Cyclique** avec intervalle configurable
- Sélecteur de **format d'affichage** (Décimal / Hexadécimal / Binaire)
- **Journal** de connexion en temps réel
- **Tableau** des valeurs des registres lus

---

## ✨ Fonctionnalités

### Interface Utilisateur
- 🔵 **Bouton de connexion coloré** : Vert = déconnecté, Rouge = connecté
- 📖 **Bouton Lire** : Lit les registres Modbus selon la fonction sélectionnée
- ✏️ **Bouton Écrire** : Écrit des valeurs dans les registres Modbus
- 🔄 **Mode Cyclique** : Lecture automatique périodique avec intervalle configurable (ms)
- 📊 **Tableau des registres** : Affichage des adresses et valeurs lues
- 📋 **Journal** : Log du statut de connexion et des opérations
- 🔢 **Format d'affichage** : Décimal, Hexadécimal (0x...) ou Binaire (0b...)
- 🔢 **Nombre de registres** : Champ configurable

### Bibliothèque Modbus TCP
Toutes les fonctions standard Modbus TCP sont implémentées :

| Code | Fonction | Description |
|------|----------|-------------|
| FC01 | Read Coils | Lire bobines (sortie discrète) |
| FC02 | Read Discrete Inputs | Lire entrées discrètes |
| FC03 | Read Holding Registers | Lire registres holding |
| FC04 | Read Input Registers | Lire registres d'entrée |
| FC05 | Write Single Coil | Écrire une bobine |
| FC06 | Write Single Register | Écrire un registre |
| FC15 | Write Multiple Coils | Écrire plusieurs bobines |
| FC16 | Write Multiple Registers | Écrire plusieurs registres |

---

## 📦 Installation

### Télécharger l'APK (Release v1.0)

Téléchargez le fichier APK depuis la section [Releases](../../releases) :

```
ModbusTcpIpv4-v1.0.apk
```

**Prérequis :**
- Android 7.0 (API 24) ou supérieur
- Activer l'installation depuis sources inconnues dans les paramètres Android

---

## 🏗️ Build depuis les sources

### Prérequis
- Android Studio (recommandé) ou Android SDK + Kotlin
- JDK 17
- Android SDK Platform 35
- Build Tools 35.x

### Avec Android Studio
1. Cloner le dépôt :
   ```bash
   git clone https://github.com/franck51000/ModbusTcpIpv4.git
   ```
2. Ouvrir le projet dans Android Studio
3. Synchroniser Gradle (nécessite accès à `dl.google.com`)
4. Build → Generate Signed Bundle/APK

### Build manuel (sans dépendances Maven/Google)
```bash
SDK=/path/to/android-sdk
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-35

# Compiler les ressources
$BT/aapt2 compile --dir app/src/main/res -o /tmp/res.zip

# Lier les ressources
$BT/aapt2 link -I $PLATFORM/android.jar /tmp/res.zip \
  --manifest app/src/main/AndroidManifest.xml \
  --java /tmp/gen -o /tmp/resources.apk

# Compiler R.java
javac -classpath $PLATFORM/android.jar -d /tmp/classes /tmp/gen/...R.java

# Compiler Kotlin
kotlinc app/src/main/java/.../*.kt \
  -classpath "$PLATFORM/android.jar:/usr/share/kotlinc/lib/kotlin-stdlib.jar:/tmp/classes" \
  -d /tmp/classes -jvm-target 1.8

# Convertir en DEX
$BT/d8 --min-api 24 --output /tmp/dex \
  /tmp/classes/**/*.class /usr/share/kotlinc/lib/kotlin-stdlib.jar

# Packager et signer
cp /tmp/resources.apk /tmp/app.apk
cd /tmp/dex && zip -u /tmp/app.apk classes.dex
$BT/zipalign -f 4 /tmp/app.apk /tmp/app-aligned.apk
$BT/apksigner sign --ks release.keystore ... /tmp/app-aligned.apk
```

---

## 📡 Utilisation

### Connexion
1. Saisissez l'**adresse IP** du serveur Modbus (ex: `192.168.1.100`)
2. Saisissez le **Port** (par défaut : `502`)
3. Saisissez le **Unit ID** (identifiant esclave, par défaut : `1`)
4. Appuyez sur **Connecter** (bouton vert → devient rouge)

### Lecture de registres
1. Sélectionnez la **fonction** (ex: `FC03 - Lire Registres Holding`)
2. Saisissez l'**adresse de départ** (ex: `0`)
3. Saisissez le **nombre de registres** à lire (ex: `10`)
4. Appuyez sur **Lire**
5. Les valeurs s'affichent dans le tableau

### Écriture de registres
1. Sélectionnez la **fonction** d'écriture (ex: `FC06 - Ecrire Registre`)
2. Saisissez l'**adresse** cible
3. Saisissez la/les **valeur(s)** dans le champ dédié (séparées par virgule pour les écriture multiples)
4. Appuyez sur **Ecrire**

### Mode Cyclique
1. Cochez **Mode Cyclique**
2. Saisissez l'**intervalle** en millisecondes (ex: `1000` pour 1 seconde)
3. La lecture se répète automatiquement

### Format d'affichage
Sélectionnez le format des valeurs dans le tableau :
- **Décimal** : `1234`
- **Hexadécimal** : `0x04D2`
- **Binaire** : `0b0000010011010010`

### Valeurs d'écriture
Les valeurs peuvent être saisies en :
- Décimal : `1234`
- Hexadécimal : `0x04D2`
- Binaire : `0b010011010010`
- Multiples valeurs séparées par virgule : `100,200,300`

---

## 🏛️ Architecture

```
app/
└── src/main/
    ├── java/com/franck51000/modbustcpipv4/
    │   ├── ModbusTcpClient.kt    # Bibliothèque Modbus TCP (FC01-FC06, FC15, FC16)
    │   └── MainActivity.kt       # Interface utilisateur principale
    ├── res/
    │   ├── layout/activity_main.xml  # Layout UI
    │   ├── values/
    │   │   ├── strings.xml       # Chaînes de caractères
    │   │   ├── themes.xml        # Thème Android
    │   │   └── colors.xml        # Couleurs
    │   └── mipmap-*/             # Icônes launcher
    └── AndroidManifest.xml       # Déclarations de l'application
```

### ModbusTcpClient.kt

La bibliothèque `ModbusTcpClient` implémente le protocole Modbus TCP/IP :

```kotlin
val client = ModbusTcpClient(
    host = "192.168.1.100",
    port = 502,
    unitId = 1,
    timeoutMs = 3000
)

client.connect()

// Lire 10 registres holding à partir de l'adresse 0
val registers = client.readHoldingRegisters(startAddress = 0, quantity = 10)

// Écrire un registre
client.writeSingleRegister(address = 5, value = 1234)

// Écrire plusieurs registres
client.writeMultipleRegisters(startAddress = 0, values = listOf(100, 200, 300))

client.disconnect()
```

**Format de trame MBAP :**
```
[TransactionID(2)] [ProtocolID=0(2)] [Length(2)] [UnitID(1)] [FunctionCode(1)] [Data...]
```

---

## 🔐 Sécurité

- Toutes les communications réseau s'effectuent dans un thread séparé (non-UI)
- Les connexions socket utilisent un timeout configurable (défaut : 3000ms)
- Les exceptions Modbus (codes d'erreur serveur) sont levées via `ModbusException`
- Aucune donnée sensible n'est stockée

---

## 📋 Changelog

### v1.0 (2026-02-22)
- ✅ Implémentation complète de la bibliothèque Modbus TCP
- ✅ Interface utilisateur intuitive
- ✅ FC01, FC02, FC03, FC04 (lecture)
- ✅ FC05, FC06, FC15, FC16 (écriture)
- ✅ Mode cyclique
- ✅ Formats Décimal / Hexadécimal / Binaire
- ✅ Journal de connexion
- ✅ Tableau des registres
- ✅ Bouton de connexion coloré
- ✅ Compatible Android 7.0+ (API 24+)

---

## 📄 Licence

Ce projet est open source. Voir le fichier [LICENSE](LICENSE) pour plus d'informations.
