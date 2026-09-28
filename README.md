# CentiTPA

**CentiTPA** ist ein sauberes, modernes Teleport-Request-System für Paper 1.21.4 Minecraft-Server.

Kein EssentialsX nötig. Alles standalone, mit klickbaren Chat-Buttons, Warmup-Timer, Schutz nach dem Teleport und konfigurierbaren Nachrichten.

---

## Features

- `/tpa <spieler>` -- Teleport-Anfrage senden
- `/tpahere <spieler>` -- Anfrage: Spieler soll zu dir kommen
- `/tpaccept [spieler]` -- Anfrage annehmen (auch klickbar im Chat)
- `/tpdeny [spieler]` -- Anfrage ablehnen (auch klickbar im Chat)
- `/tpcancel` -- Eigene Anfrage zurueckziehen
- `/tptoggle` -- Eingehende Anfragen de-/aktivieren
- **Warmup-Timer** -- Countdown vor dem Teleport, bricht bei Bewegung ab
- **Mehrere Anfragen** -- Ein Spieler kann von mehreren Spielern gleichzeitig angefragt werden
- **Cooldown** -- Konfigurierbare Wartezeit zwischen Anfragen
- **Post-Teleport-Schutz** -- Kurze Unverwundbarkeit nach dem Teleport
- **Timeout** -- Anfragen laufen automatisch ab
- **Clickable Chat** -- [ANNEHMEN] und [ABLEHNEN] Buttons direkt im Chat
- **Vollstaendig konfigurierbar** -- Alle Nachrichten, Zeiten und Sounds in config.yml

---

## Befehle

| Befehl | Beschreibung | Permission |
|:---|:---|:---|
| `/tpa <spieler>` | Teleport-Anfrage senden | `centitpa.use` |
| `/tpahere <spieler>` | Spieler zu sich anfordern | `centitpa.use` |
| `/tpaccept [spieler]` | Anfrage annehmen | `centitpa.use` |
| `/tpdeny [spieler]` | Anfrage ablehnen | `centitpa.use` |
| `/tpcancel` | Eigene Anfrage zurueckziehen | `centitpa.use` |
| `/tptoggle` | TPA-Anfragen ein-/ausschalten | `centitpa.toggle` |

**Aliases:** `/tpaccept` = `/tpyes` | `/tpdeny` = `/tpno` | `/tptoggle` = `/tpaoff` / `/tpaon`

---

## Permissions

| Permission | Standard | Beschreibung |
|:---|:---|:---|
| `centitpa.use` | Alle | Alle TPA-Grundbefehle |
| `centitpa.toggle` | Alle | TPA-Anfragen deaktivieren |
| `centitpa.bypass.cooldown` | OP | Cooldown umgehen |
| `centitpa.bypass.movecancel` | OP | Warmup wird durch Bewegung nicht abgebrochen |

---

## Installation

1. JAR-Datei in den `plugins/` Ordner kopieren
2. Server (neu-)starten
3. Fertig -- keine weiteren Abhaengigkeiten noetig

---

## config.yml

```yaml
timing:
  request-timeout: 60   # Sekunden bis Anfrage ablauft
  send-cooldown: 10     # Sekunden zwischen zwei Anfragen
  warmup: 5             # Countdown vor dem Teleport
  protection: 5         # Schutz-Sekunden nach dem Teleport
```

Alle Nachrichten und Sounds sind ebenfalls in der `config.yml` aenderbar.

---

## Kompatibilitaet

- Paper 1.21.4+
- Java 21+
- Keine weiteren Abhaengigkeiten

---

*Entwickelt von Centi09 -- centi09.de*
