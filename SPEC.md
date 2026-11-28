# faux-person-jpa: specification

A small Java command-line application that shows how to use
[faux-person](https://github.com/tauasa/faux-person): it generates random
people and saves them to a database through JPA.

| | |
|---|---|
| Coordinates | `org.tauasa:faux-person-jpa:0.1.0-SNAPSHOT`, not published |
| Package | `org.tauasa.fauxjpa` |
| Java | 17 (`maven.compiler.release=17`) |
| Build | Maven |
| Dependencies | `org.tauasa:faux-person:0.4.0`, Hibernate ORM 7.4 (Jakarta Persistence 3.2), H2 2.5. JUnit 6 at test scope |
| Status | Draft, 2026-11-20 |

## Goals

1. One command generates people and saves them: `java -jar ... -n 500`.
2. **Every field faux-person makes is stored**, and a saved person reads back
   as the same `org.tauasa.faux.model.Person`, field for field.
3. **Companies are stored once.** Employers repeat, so each company is one row
   that people point at, keyed by its domain, across runs as well as within one.
4. **Reproducible.** The same seed gives the same people, as faux-person does.
5. Works with no setup: the default database is an H2 file under `./data/`.

## Non-goals

- A web or REST layer, or Spring. This shows plain JPA.
- Databases other than H2 out of the box. Another database needs its JDBC
  driver on the classpath and its URL in `--db`.
- Managing credentials. H2's default user is used, with no password.
- Schema migrations. Hibernate creates or updates the tables on start.

## 1. Data model

Two entities and two embeddables. Enums are stored by name, never by
position, so adding a region to faux-person can't change what a stored value
means.

```
person                                       company
----------------------------------------     ---------------------------
id                 BIGINT  PK (sequence)     id      BIGINT  PK (sequence)
first_name         VARCHAR NOT NULL          name    VARCHAR NOT NULL
last_name          VARCHAR NOT NULL          city    VARCHAR NOT NULL
native_first_name  VARCHAR                   state   VARCHAR NOT NULL
native_last_name   VARCHAR                   country VARCHAR
native_full_name   VARCHAR                   domain  VARCHAR NOT NULL UNIQUE
gender             VARCHAR NOT NULL (enum)
region             VARCHAR NOT NULL (enum)
date_of_birth      DATE    NOT NULL
email              VARCHAR NOT NULL
phone              VARCHAR NOT NULL
street             VARCHAR NOT NULL
city               VARCHAR NOT NULL
state              VARCHAR NOT NULL
postal_code        VARCHAR NOT NULL
country            VARCHAR NOT NULL
employer_id        BIGINT  NOT NULL FK -> company.id
```

| Type | Maps | How |
|---|---|---|
| `PersonEntity` | `Person` | `@Entity`, table `person`. `name` and `address` are embedded; `employer` is `@ManyToOne` to `CompanyEntity` |
| `NameEmbeddable` | `Name` | `@Embeddable`: `first_name`, `last_name`, and the native name's `native_first_name`, `native_last_name` and `native_full_name`, all null when the name has none |
| `AddressEmbeddable` | `Address` | `@Embeddable`: `street`, `city`, `state`, `postal_code`, `country` |
| `CompanyEntity` | `Company` | `@Entity`, table `company`, `domain` unique. `state` is empty outside the US, and a null `country` reads back as `US` |

Each type has a `from(...)` that copies a faux-person record in and a
`to...()` that builds the record back, so goal 2 is one `equals` check.

The native-name columns and `company.country` are nullable, so Hibernate's
`update` can add them to a database written on faux-person 0.2.0. Every
company in one of those is a US company, which is why a null country reads
back as `US`.

Marked and real companies are different rows: `costco.example` and
`costco.com` are different domains.

## 2. Saving

`PeopleStore` wraps an `EntityManagerFactory`:

| Method | Does |
|---|---|
| `saveAll(List<Person>)` | Saves the people in one transaction. Each employer is looked up by domain, first in the transaction's own map, then in the database, and created only if neither has it. Flushes and clears every 50 people, with JDBC batching at 50 |
| `count()` | Number of people stored |
| `countCompanies()` | Number of companies stored |
| `countByRegion()` | People per region, most first, then by region name |
| `findByLastName(String)` | People with that surname, by id, as faux-person `Person`s |

A failure rolls the whole transaction back, so a run saves all its people or
none.

## 3. The command line

`java -jar target/faux-person-jpa-<version>.jar [options]`

| Option | Does | Default |
|---|---|---|
| `-n, --count N` | How many people to generate and save | 100 |
| `-s, --seed S` | Seed, passed to faux-person | random |
| `-r, --region R` | A faux-person region in lowercase, or `mixed` | `mixed` |
| `--real-companies` | Real employer names and domains, as faux-person's switch | marked (`[faux]`, `.example`) |
| `--db URL` | JDBC URL | `jdbc:h2:file:./data/faux-people` |
| `-h, --help` | Show the options | |

It prints the seed to standard error, then how many people it saved, the
totals in the database, and the five largest regions. A bad argument exits 2
with the usage; a database failure exits 1 with its message. Each run adds to
what's already stored.

## 4. Tests

`mvn verify` runs them against an in-memory H2 database.

| Area | Fails when |
|---|---|
| 500 people saved and read back equal the generated ones, in order | A field isn't stored, or is stored wrong |
| Companies: one row per distinct employer domain, after two runs too | Employers are duplicated, or merged when they differ |
| Marked and real runs of the same seed store different company rows | `--real-companies` and the default collide |
| South Korean people read back with their Hangul names | The native name isn't stored |
| Japanese people's employers read back with their country | The company's country isn't stored |
| A company row with no country reads back as a US company | A database from faux-person 0.2.0 stops reading back |
| `countByRegion` sums to `count` and is sorted | The summary lies |
| `findByLastName` returns only that surname | The query leaks other people |
| A failed save leaves nothing behind | A half-saved run survives |
| CLI: saves to the URL given, totals grow across runs, bad arguments exit 2 | The tool's contract breaks |

## 5. Repo and CI

- Public repo `tauasa/faux-person-jpa`, MIT license, with `main` protected.
- `.githooks/pre-commit` from Sanvio Factory, with `core.hooksPath` set.
- `.github/workflows/build.yml` runs `mvn -B verify` on JDK 17, for when it's pushed.
