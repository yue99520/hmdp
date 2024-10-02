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
- The server is scalable, because I use redis to store the verifying code and user session.
- The session will be extended by user continuously interacting with the server.
- I didn't use the spring session because this is a practice project.
