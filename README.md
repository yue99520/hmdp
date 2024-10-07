# Heima Redis Practice Project

## Introduction
This is a practice project focused on Redis integration. 
Some parts of the project are provided by the Heima internet course. 
I implemented several common and important usages of Redis.

## Architecture
![img.png](readme/img.png)

## Features that are implemented by myself:
### User login / Shared session
#### Introduction
- User can choose fast log in with phone number, 
server will send a code (implemented by printing on the server console log) for verification.
- After user are verified, the server will generate a token and store it in Redis.
#### Selling Points
- I didn't use the spring session because this is a practice project.
- Scalable
  - The verification code and user session are stored in Redis, so the server is horizontally scalable.
- Extendable session
  - The session will be extended by user continuously interacting with the server.

### Caching on shop APIs:
#### Introduction
- User's shop query API results are cached in Redis, speeding up the response time.
- The cache is invalidated actively when the shop data is updated, the cache rebuilding is depending on the "Repair on Read" strategy.
#### Selling Points
- Cache Penetration Protection
  - The invalid or doesn't exist shop data will also be cached (in empty string) for a short time.
- Three strategy for the cache rebuilding:

| Strategy         | Description                                                   | Consistency | DB pressure    | Response efficiency |
|------------------|---------------------------------------------------------------|-------------|----------------|---------------------|
| simple           | no concurrency protection                                     | Higher      | Many in a time | Lower               |
| mutex            | blocks threads using a mutex lock while rebuilding the cache  | Higher      | One in a time  | Lower               |
| logic-expiration | returns old cache data and asynchronously rebuilds the cache  | Lower       | One in a time  | Higher              |