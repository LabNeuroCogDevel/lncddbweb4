-- name: list-person-only-notes
-- list notes of one person
select * from person_note natural join note
  where pid = :pid::integer;

-- name: insert-note-now<!
insert into note (pid,ra,ndate,note) values (:pid::integer,:ra,now(),:note)

-- name: insert-note-vid<!
insert into visit_note (vid,nid) values (:vid::integer,:nid::integer)

-- name: insert-person-note!
insert into person_note (pid,nid) values (:pid::integer,:nid::integer)

