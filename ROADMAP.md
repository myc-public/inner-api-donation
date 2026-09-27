# Roadmap — inner-donation-api

## 🔲 TODO — Sécurité : OAuth2, RBAC, ABAC, Keycloak

> Statut : **non démarré**. La sécurité métier n'est pas encore intégrée : `myc.security.enabled: false` dans `application.yml`, aucune surcharge par environnement. Les endpoints `/api/v1/**` sont donc ouverts sur tous les environnements.

- [ ] **Keycloak** : serveur d'autorisation (realm, clients, rôles, client scopes) en remplacement d'ADFS
- [ ] **OAuth2 resource server** : validation des JWT émis par Keycloak (issuer, JWKS, audience), sécurité activée sur tous les environnements
- [ ] **RBAC** : rôles Keycloak mappés en autorités Spring, contrôle d'accès par opération
- [ ] **ABAC** : règles fondées sur les attributs du token et de la ressource (ex. un donateur n'accède qu'à ses propres dons)
- [ ] Contrat OpenAPI : schéma de sécurité Keycloak et scopes / rôles déclarés par opération (pratique P12 de [API_FIRST.md](API_FIRST.md))
- [ ] Tests : sans token → 401, token sans le scope ou le rôle → 403, token valide → 200, règles ABAC

Acquis du lot L0 (API First), à reprendre dans cette roadmap :
- Le scope `inner:donation` est exigé sur `/api/v1/**` quand la sécurité est activée (le matcher ciblait `/v1/**` et laissait passer donors et donations).
- Sans la propriété `myc.security.enabled`, c'est la chaîne sécurisée qui est créée, et non plus les deux chaînes à la fois.
- `SecurityConfigTest` couvre les cas 401 / 403 / 200 avec la sécurité activée.
