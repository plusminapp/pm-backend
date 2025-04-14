# pm-backend (Kotlin)

De pm-backend is de backend repo voor de PlusMin app, gebouwd met Kotlin.
Op dit moment wordt voor de backend nog geen gebruik gemaakt van devcontainers want Intellij
is op dit moment nog minder volwassen op devcontainer gebied dan Vscode.
Waarschijnlijk is ook een betaalde Ultimate Edition nodig voor ondersteuning.
Daarnaast is devcontainers op Intellij nog EAP (Early Access Program) en is SSH nodig om
git te benaderen.

## Info over pm-backend
Lorum ipsum shizzle

## Ontwikkel omgeving
Omdat devcontainers nog niet gebruikt worden, ga naar de requirements [notes](NOTES.md) voor de lokale 
ontwikkelomgeving opzet voor de backend.

## Runtime omgeving
Docker compose (v2) is nodig om in de achtergrond de benodigde componenten te draaien.
Docker compose (v2) zit al geinstalleerd in Docker desktop. Docker desktop is echter gebonden aan een licentie
indien de organisatie meer dan 250 werknemers heeft (TODO voorwaarden checken?). 
Goed alternatief is Podman desktop.
Zie hiervoor ook [notes](NOTES.md)
