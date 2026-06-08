create table app_user (
  user_id        varchar(36) primary key,
  display_name   varchar(64) not null,
  created_at_utc timestamp not null,
  active         boolean not null default true,
  constraint uq_app_user_display_name unique (display_name)
);

create table dictionary_pair (
  pair_id            varchar(64) primary key,
  language_code      varchar(16) not null,
  from_word          varchar(200) not null,
  to_word            varchar(200) not null,
  example            varchar(500) not null default '',
  global_enabled     boolean not null,
  created_at_utc     timestamp not null,
  updated_at_utc     timestamp not null
);

create table score_attempt (
  attempt_id         bigint generated always as identity primary key,
  user_id            varchar(36) not null references app_user(user_id),
  pair_id            varchar(64) not null references dictionary_pair(pair_id),
  mode               varchar(32) not null,
  result             char(1) not null check (result in ('S', 'F')),
  attempted_at_utc   timestamp not null
);

create index ix_attempt_user_pair_mode_time
  on score_attempt(user_id, pair_id, mode, attempted_at_utc desc);

create table score_progress (
  user_id            varchar(36) not null references app_user(user_id),
  pair_id            varchar(64) not null references dictionary_pair(pair_id),
  mode               varchar(32) not null,
  history_last12     varchar(32) not null,
  success_count      smallint not null,
  total_count        smallint not null,
  success_percent    smallint not null,
  updated_at_utc     timestamp not null,
  primary key (user_id, pair_id, mode)
);
