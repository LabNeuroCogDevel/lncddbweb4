-- name: list-person-only-notes
-- list notes of one person
select * from person p natural join note n
   natural left join visit_note vn
   where p.pid = :pid::integer and vid is null;


-- name: insert-note-now<!
insert into note (pid,ra,ndate,note) values (:pid::integer,:ra,now(),:note)

-- name: insert-note-vid<!
insert into visit_note (vid,nid) values (:vid::integer,:vid::integer)

