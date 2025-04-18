# Ontwikkelomgeving
Initieel de lokale ontwikkelomgeving inrichten zoals hieronder beschreven.
Voor de avonturiers is er ook een devcontainer mogelijkheid. Het opzetten daarvan heeft wat meer 
uitdagingen dan bij vscode. Indien je devcontainers wilt proberen dan volg o.a. de requirements van
Jetbrains en zie ook de tips onderaan deze NOTES.md.

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

### Devcontainer opzetten.
LET OP: onderstaande devcontainer opzet werkt met Podman. De "podman/podman desktop/podman compose" opzet heeft zelf ook weer een aantal
uitdagingen. Docker desktop en plain docker is niet getest. Wellicht komt er nog een instructie voor gebruik podman.

Nodig is SSH_AGENT forwarding op je lokale omgeving omdat git in de devcontainer met ssh werkt.
De ssh_agent wordt op een Mac default opgestart.
Uiteraard moet je dan een SSH key hebben of genereren. Info o.a. op https://docs.github.com/en/authentication/connecting-to-github-with-ssh

Voor het agent verhaal zie ook https://docs.github.com/en/authentication/connecting-to-github-with-ssh/using-ssh-agent-forwarding
In bovenstaande link is vooral belangrijk "ssh-add --apple-use-keychain YOUR-KEY" voor mac users.

Lokaal moet ingeregeld zijn:
- een .ssh/config file met inhoud als:
``` 
  Host github.com
  AddKeysToAgent yes
  UseKeychain yes
  ForwardAgent yes
  IdentityFile ~/.ssh/id_ed25519_devcont
```

De IdentityFile naam id_ed25519_devcont in dit voorbeeld is de pubieke key die voor de user in github.com is opgenomen.

- Niet zeker maar bij niet werkende omgeving zou je ook in de /etc/sshd_config file "AllowAgentForwarding yes" kunnen enablen.

Nadat de container is opgestart wordt er gevraagd of je intellij toestaat om een ssh key te gebruiken. Klik OK.
Vink bij de popup die daarna volgt aan dat je de SSH forwarding wilt gebruiken.

Er zijn mounts gebruikt om een oplossing te hebben voor git en maven repo.
- Git heeft user en email nodig. De global git config van lokaal komt niet mee dus als oplossing gemount.
- De camt053parser bestaat alleen in de lokale repo en niet remote, want eigen jar. Daarom mount toegvoegd
  zodat de camt053parser door maven gevonden wordt. Bijkomend voordeel, alles wat je toevoegt in de container komt
  lokaal in je maven repo.



