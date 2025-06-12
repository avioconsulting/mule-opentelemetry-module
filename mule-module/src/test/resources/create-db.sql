CREATE SCHEMA testdb;
 
CREATE TABLE testdb.users 
(id INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 5, INCREMENT BY 1), 
userId INTEGER, 
username varchar(50),
CONSTRAINT users_primary_key PRIMARY KEY (id));


DELETE from testdb.users;
INSERT INTO testdb.users (userId, username)
values
(100, 'User1'),
(200, 'User2'),
(300, 'User3')