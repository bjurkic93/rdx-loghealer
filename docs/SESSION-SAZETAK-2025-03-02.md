# Sažetak razvojnog sessiona – LogHealer (2. ožujka 2025)

## Pregled

Ovaj dokument bilježi ključne probleme i rješenja tijekom razvoja LogHealer projekta.

---

## 1. Liquibase migracija – duplikati `project_key`

**Problem:** Migracija `008-project-key.xml` nije mogla kreirati unique constraint jer su u koloni `project_key` postojali duplikati (npr. `rdx-school-admin-fe` više puta).

**Rješenje:** Dodan novi changeset `008-2b` koji prije kreiranja unique constrainta popravlja duplikate dodavanjem UUID sufiksa starijim projektima.

**Datoteka:** `backend/src/main/resources/db/changelog/008-project-key.xml`

---

## 2. Ručno čišćenje duplikata na produkciji

**Problem:** Changeset `008-2b` se nije izvršio jer je Liquibase već prošao `008-2`, a `008-3` je failao.

**Rješenje:** Ručno izvršen SQL na produkciji:
```sql
DELETE FROM project;
```

**Napomena:** Baza je `rdx_loghealer_db` (ne `loghealer_db`).

---

## 3. Projekti se kreiraju ali ne prikazuju u listi

**Problem:** `GET /api/v1/projects` vraćao je prazan array iako su projekti postojali u bazi.

**Uzrok:** Kolona `is_active` imala je default `false` u bazi, a Lombok `@Builder` ignorira Java default vrijednosti polja.

**Rješenje:** Dodana `@Builder.Default` anotacija na `active` polje u `Project.java` entitetu.

**Datoteka:** `backend/src/main/java/com/reddiax/loghealer/entity/Project.java`  
**Commit:** `fix: add Builder.Default for active field in Project entity`

---

## 4. Tenant konfiguracija

- Postoje dva tenanta: `default` i `reddia-x`
- Projekti koriste tenant `reddia-x`
- `ProjectController.getOrCreateDefaultTenant()` traži tenant po imenu `reddia-x`

---

## Korisne SQL naredbe za produkciju

```bash
# Spoji se na bazu
docker exec -it loghealer-postgres-1 psql -U loghealer -d rdx_loghealer_db
```

```sql
-- Provjeri projekte
SELECT p.id, p.name, p.tenant_id, p.is_active, t.name as tenant_name 
FROM project p 
JOIN tenant t ON p.tenant_id = t.id;

-- Aktiviraj sve projekte
UPDATE project SET is_active = true;

-- Provjeri tenante
SELECT * FROM tenant;
```

---

## Važne napomene

| Stavka | Vrijednost |
|--------|------------|
| Kolona u bazi | `is_active` (ne `active`) |
| PostgreSQL korisnik | `loghealer` |
| Baza | `rdx_loghealer_db` |
| Docker container | `loghealer-postgres-1` |
