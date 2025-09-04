# Ontwikkelomgeving

Om zo min mogelijk omgevingsproblemen te hebben (works on my machine) zullen alle ontwikkelaars 
dezelde versies van de ontwikkelstack zelf moeten installeren/configureren. 
Voorlopig daarom onderstaande settings hanteren.

Aangeraden wordt om SDKMAN of Mise te gebruiken (eis?).

### Lokale omgeving

- `Java 17.0.14-tem`
- `Maven 3.9.9`
- `git version 2.49.0`


### Containers
De runtime omgeving wordt door "docker compose" (V2) in de lucht gebracht.
Deze runtime omgeving bestaat uit de componenten pm-database, pm-backend en pm-frontend.
De omgevingen zijn lcl (lokaal), dev (development remote), stg (staging) en .....

Docker Desktop (met out-of-the-box docker compose v2) of Podman Desktop is nodig om makkelijker
de container en images te beheren.
Podman heeft ook "docker-compose" nodig met een aanpassing in .docker/config.json zodat
het command "docker compose" (zonder hyphen) kan worden gevonden. Daarna kan "podman compose" worden
gebruikt (overal waar docker xxxx staat kan op de commandline worden vervangen door podman)

Spul kan met homebrew worden geinstalleerd.

- `Docker Compose version 2.34.0`
- `Docker version 28.0.4`
- `podman version 5.4.1`
- `podman desktop >= v1.16.2`

### Intellij Plugins
Lorum ipsum shizzle

### Formatting/linting
Lorum ipsum shizzle

# GitHub
De PlusMin repos worden gehost op GitHub.
Om eenvoudiger te kunnen werken zul je even wat moeten investeren in SSH toegang. HTTPS is ook mogelijk (maar
werkt niet met Intellij devcontainers). HTTPS werkt met de github credential manager.
- Aanmaken private en pub key (ed25519)
- Linken met jouw GitHub account (authentication (en eventueel signing?))
- zie ook https://docs.github.com/en/authentication/connecting-to-github-with-ssh
  , https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints
  en bij problemen ook https://docs.github.com/en/authentication/troubleshooting-ssh

NB mocht je PlusMin als HTTPS repo hebben uitgechecked EN je wilt SSH gaan doen, dan moet de git remote url worden gereset.
Een snelle fix hiervoor is in je git global config het volgende snippet op te nemen:
````
[url "git@github.com:"]
  insteadOf = https://github.com/
````
