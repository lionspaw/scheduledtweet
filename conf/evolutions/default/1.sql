# --- First database schema

# --- !Ups

create table if not exists tweets (
  id int not null auto_increment primary key,
  text varchar(140) not null,
  path varchar(1000) not null,
  state int default 0,
  date varchar(100)
);

# --- !Downs

drop table if exists tweets;
