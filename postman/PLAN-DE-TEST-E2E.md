# Plan de test de bout en bout : donation-api securisee (lots K1 a K5)

## 1. Objectif et perimetre

Verifier de bout en bout, sur une machine de developpement, que la chaine securisee de donation-api fonctionne
et resiste aux abus :

| Lot | Ce qui est verifie |
|---|---|
| K1 | Keycloak as code : deux realms en ligne, clients, flux autorises et refuses |
| K2 | L'API exige un token valide (signature, emetteur, audience) et le bon scope |
| K3 | Matrice RBAC (modele C) : chaque role n'a que ses permissions |
| K4a / K4b | Identites internes / externes, contrat de claims (`party_id`, `actor_type`, `permissions`, `team_id`) |
| K4c | ABAC donateur : endpoints `/me`, aucun acces aux donnees d'un autre |
| K4d | Audit : toute modification / suppression d'une donation produit une ligne `audit_outbox` |
| K5 | Passerelles : WAF, liste blanche, tokens falsifies, scopes, en-tetes, isolation reseau, limitation de debit |

Hors perimetre : front + BFF (K6), contrat OpenAPI (K7), observabilite securite (K8), OpenShift.

## 2. Environnements

| Mode | Chaine | Environnement Postman | Usage |
|---|---|---|---|
| **dev** | Postman -> API (`http://localhost:8080`) et Keycloak (`http://localhost:8180`) en direct | `e2e-dev` | Developpement : K1 a K4 |
| **pile** | Postman -> APISIX externe (`https://localhost:8443`) -> APISIX interne -> API | `e2e-pile` | **Recette** : K1 a K5, chaine complete |

Le dossier **08 (passerelles)** ne s'execute qu'en mode pile (ignore en mode dev). En mode pile, `/management/**`
et `/docs/**` ne sont pas exposes (attendu : 404).

## 3. Prerequis

- Mode dev : `gitops-platform/local/keycloak` et `inner-donation-api/local` demarres, API lancee (`mvn spring-boot:run`).
- Mode pile : `gitops-platform/local/stack/README.md` (certificat, `mvn package`, `docker compose up -d --build`),
  **mode dev arrete**.
- Environnement Postman : recopier les trois secrets depuis le `.env` du mode utilise
  (`DONATION_SERVICE_CLIENT_SECRET`, `DONATION_TESTS_CLIENT_SECRET`, `TEST_USERS_PASSWORD`).
  Ne jamais exporter un environnement rempli dans le depot.
- Mode pile : certificat auto-signe -> Postman, Settings > General > SSL certificate verification : OFF.

## 4. Execution

**Collection Runner de Postman** : collection `e2e-donation`, environnement `e2e-pile` (ou `e2e-dev`),
toute la collection, dans l'ordre. Les tokens durent 5 minutes : relancer toute la collection plutot qu'un dossier.

**Newman (optionnel)**, environnement rempli hors du depot :

```powershell
npx -y newman@6 run inner-donation-api\postman\e2e-donation.postman_collection.json -e $env:TEMP\e2e-pile.json --insecure
```

Puis les controles complementaires (section 6).

## 5. Cas de test de la collection `e2e-donation`

| Dossier | Cas | Attendu |
|---|---|---|
| 00 Sante et Keycloak | S1-S4 sonde, info, loggers, documentation | dev : 200 / 200 / 401 / 200 ; pile : 404 (non exposes) |
| | K1a-K1b decouverte OIDC des deux realms | 200, `issuer` = `keycloakUrl` + realm |
| 01 Tokens | client credentials (`donation-service`, avec / sans scope) | 200, claims du contrat |
| | code + PKCE : admin.test, agent.casa, superviseur.casa, donor.a, donor.b, benef.a | 302 puis 200 ; permissions, `party_id`, `actor_type` attendus par role |
| 02 Authentification | A1 sans token / A5 token altere | 401 + `WWW-Authenticate: Bearer` |
| | A3 ecriture avec scope lecture / A4 sans scope | 403 |
| 03 Matrice RBAC | R4-R19 : agent, admin, superviseur, beneficiaire, donateur | 2xx si la permission est presente, 403 sinon (corps `problem+json`) |
| 04 CORS | origine etrangere | refusee |
| 05 Console de compte | agent.casa, donor.a | API de compte 200, `party_id` invisible |
| 06 ABAC donateur | C1-C12 | profil = `party_id`, don d'un autre -> 404, sans profil -> 409, internes sans `:own` -> 403 |
| 07 Audit | A1-A2 claims `team_id` / `preferred_username` ; A3-A8 | creation non auditee ; PATCH / DELETE admin audites ; 403 / 404 non audites |
| 09 Parcours de bout en bout | P1-P9 : donor.a cree un don, agent le retrouve, agent ne peut pas le corriger, admin le corrige, donor.a voit la correction, donor.b ne le voit pas, admin le supprime, donor.a ne le trouve plus | 201, 200, 403, 200, 200 (90), 404, 204, 404 |
| 08 Passerelles (pile) | G1 injection SQL en parametre, G3 traversee de chemin | 403 par le WAF (corps sans `/problem/`) |
| | G2 XSS dans un corps JSON | **ignore** : limite acceptee (section 8), a reactiver avec l'option B (dette 16) |
| | G4-G5 donnees legitimes (accents, apostrophe, tiret) | 201 puis 204 (pas de faux positif) |
| | G6-G8 console admin Keycloak, realm master, chemin inconnu | 404 |
| | G9 sans token, G10 emetteur inconnu | 401 par APISIX interne (« emetteur non reconnu »), l'API n'est pas appelee |
| | G11 token falsifie (scope et permissions ajoutes, signature d'origine) | 401 |
| | G12 scope lecture seule en ecriture | 403 par APISIX interne |
| | G13-G14 en-tetes sur l'API et sur Keycloak | HSTS, `nosniff`, `SAMEORIGIN`, `Referrer-Policy`, `X-Request-Id` |

## 6. Controles complementaires (PowerShell)

**6.1 Audit du parcours (dossiers 07 et 09)** : 2 lignes par donation modifiee puis supprimee.

```powershell
cd D:\workspace\public\gitops-platform\local\stack     # mode pile ; mode dev : inner-donation-api, -f local/compose.yaml
$sql = @'
SELECT event_type, aggregate_id, JSON_UNQUOTE(JSON_EXTRACT(payload, '$.actor.username')) AS auteur,
       JSON_EXTRACT(payload, '$.changes') AS changes, JSON_UNQUOTE(JSON_EXTRACT(payload, '$.occurredAt')) AS quand
FROM audit_outbox ORDER BY occurred_at DESC LIMIT 6;
'@
$sql | docker compose exec -T donation-api-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -t -u "$MYSQL_USER" "$MYSQL_DATABASE"'
```

Attendu pour `e2eDonationId` (console Postman) : `DonationUpdated` (`75.00` -> `90.00`) puis `DonationDeleted`,
auteur `admin.test`, `occurredAt` a la milliseconde.

**6.2 Isolation reseau (mode pile)** : seul 8443 repond.

```powershell
8080, 8180, 3307, 8025, 9080, 8443 | ForEach-Object {
  $ouvert = Test-NetConnection -ComputerName localhost -Port $_ -WarningAction SilentlyContinue -InformationLevel Quiet
  "{0,-5} {1}" -f $_, $(if ($ouvert) { 'OUVERT' } else { 'ferme' }) }
```

Attendu : `8443 OUVERT`, tous les autres `ferme`.

**6.3 Limitation de debit (mode pile)** : 120 requetes simultanees depuis le reseau interne
(Postman et un poste Windows ne depassent pas ~16 req/s, insuffisant pour declencher la limite).

```powershell
cd D:\workspace\public\gitops-platform\local\stack
$lua = @'
local http = require("resty.http")
local codes, t = {}, {}
for i = 1, 120 do
  t[i] = ngx.thread.spawn(function()
    local r = http.new():request_uri("http://apisix-ext:9080/realms/myc-internal/.well-known/openid-configuration")
    return r and r.status or 0
  end)
end
for i = 1, 120 do local _, s = ngx.thread.wait(t[i]); codes[s] = (codes[s] or 0) + 1 end
for k, v in pairs(codes) do print("HTTP ", k, " : ", v) end
'@
$lua | docker compose exec -T apisix-int sh -c 'cd /usr/local/apisix && resty -c 512 -I deps/share/lua/5.1 /dev/stdin'
```

Attendu : environ 60 a 70 `HTTP 200` (20 req/s + rafale de 40) et le reste en `HTTP 429`.

**6.4 Journal du WAF (mode pile)** : chaque blocage est trace avec la regle et le `request_id`.

```powershell
docker compose logs apisix-ext | Select-String "Coraza: Access denied" | Select-Object -Last 5
```

## 7. Criteres d'acceptation

- Collection `e2e-donation` : toutes les assertions vertes (G2 ignore, section 8).
- 6.1 : lignes d'audit conformes ; 6.2 : seul 8443 ouvert ; 6.3 : des 429 presents ; 6.4 : blocages traces.
- Aucun secret dans le depot (environnements Postman versionnes vides, `.env` et `tls/` ignores).

## 8. Limites connues

| Limite | Effet | Suite |
|---|---|---|
| WAF Coraza dans APISIX : corps de requete non inspectes (`wasm_process_req_body` non demande par `coraza-proxy-wasm`) | Une injection dans un corps JSON ou un formulaire n'est pas bloquee a la bordure (G2) ; l'API reste protegee par la validation, les requetes parametrees et le token Limite acceptee (decision K5 du 2026-10-08, option A). Passage a un WAF dedie qui inspecte les corps (option B) apres le deploiement sur le Sandbox OpenShift : dette 16 |
| Limitation de debit par `remote_addr` | Derriere le routeur OpenShift, toutes les requetes auraient la meme IP | `real-ip` a configurer au deploiement OpenShift |
| Certificat auto-signe en local | Verification SSL a desactiver dans Postman | Route TLS sur OpenShift |
