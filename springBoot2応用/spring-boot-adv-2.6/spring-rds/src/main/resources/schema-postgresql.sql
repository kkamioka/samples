CREATE TABLE IF NOT EXISTS teacher (
  /*
    PostgreSQLでは、AUTO_INCREMENTが使えないのでSERIALにします
  */
  id SERIAL NOT NULL PRIMARY KEY,
  user_name VARCHAR(60) NOT NULL,
  email VARCHAR(254) NOT NULL
);
