-- name: list-contacts
-- returns
--  pid,relation,who,lastcontact,contacts:[{cid,ctype,cvalue,nogood,note:[{note,ndate,ra}] }]
select * from contacts_view where pid = :pid::integer;


-- name: insert-contact!
-- :pid::integer,:ctype,:cvalue,:who,:relation
insert into contact (pid,ctype,cvalue,who,relation) values (:pid::integer,:ctype,:cvalue,:who,:relation);

-- name: contact-note!
-- assoc note and contact
insert into contact_note (nid,cid) values (:nid::integer, :cid::integer)

-- name: contact-nogood!
-- make a contact as no longer useable
update contact set nogood = true where cid = :cid::integer
