-- name: insert-note-now<!
insert into note (pid,ra,ndate,note) values (:pid::integer,:ra,now(),:note)

-- name: insert-note-vid<!
insert into visit_note (vid,nid) values (:vid::integer,:vid::integer)
