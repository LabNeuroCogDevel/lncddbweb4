-- name: list-visits-by-pid
--  need :pid, will give visits with study
-- TODO: get dropped!
select 
  v.age,v.vid,v.pid,
  vtype,age,vtimestamp,
  vscore,visitno,vstatus,
  vs.study,vs.cohort,
  googleuri
 from visit as v 
 join visit_study as vs on v.vid=vs.vid
 where v.pid = :pid
 order by v.age desc
 -- group by vid
 -- round(v.age::numeric,1) as v.age, 

-- name: list-notes-by-vid 
select n.*,dc.droplevel from note n 
  join visit_note vn on vn.nid=n.nid  and editof is null
  left join visit_drop vd on vd.vid=vn.vid
  left join dropped d on d.did=vd.did
  left join dropcode dc on dc.dropcode = d.dropcode
  where vn.vid=:vid 

-- name: list-tasks-by-vid
-- get tasks from a vid
select vtid,task,measures is not null as hasdata 
 from visit_task 
 where vid=:vid 

--name: get-visit-task-by-id
select p.pid,v.vid,v.age,v.vtimestamp,p.fname,p.lname,p.sex,vt.* 
 from visit_task as vt 
 join visit as v on v.vid=vt.vid
 join person as p on p.pid=v.pid
 where vtid = :vtid




-- name: insert-newvisit<!
-- inserts a visit
insert into visit 
 (pid,age,vtype,vtimestamp,visitno,vstatus) 
 select 
   :pid::integer as pid, 
   date_part('day',(:vtimestamp::timestamp-dob))/365.25 as age,
   :vtype as vtype,
   :vtimestamp::timestamp as vtimestamp,
   :visitno::integer as visitno,
   'sched' as vstatus
  from person where pid = :pid::integer
-- vstatus opts: ('sched','complete','checkedin','cancelled','noshow','unkown','other')

-- name: test-age
-- test above insert-newvisit select command
select 
   :pid::integer as pid, 
   date_part('day',(:vtimestamp::timestamp-dob))/365.25 as age,
   :vtimestamp::timestamp as vtimestamp,
   'sched' as vstatus
  from person where pid = :pid::integer


-- name: insert-visittask!
insert into visit_task (vid,task,measures,files) values (:vid,:task,:measures::jsonb,:files::jsonb)


-- name: insert-visitaction!
insert into visit_action (vid,action,ra,vatimestamp) values (:vid,:action::status,:ra,:vatimestamp::timestamp)

-- name: insert-visitaction-now!
insert into visit_action (vid,action,ra,vatimestamp) values (:vid,:action::status,:ra,now())

-- name: insert-visitstudy! 
insert into visit_study (vid,study,cohort) values (:vid,:study,:cohort)

-- name: insert-visitnote<! 
insert into visit_note (vid,nid) values (:vid,:nid)

-- name: insert-visitdrop! 
insert into visit_drop (vid,did) values (:vid,:did)


-- name: list-visit_notes
--  need :vid, will give visit notes
select n.* from note n 
 join visit_note vn on vn.nid=n.nid 
 where vid = :vid

-- name: list-visit_tasks
--  need :vid, will give visit tasks
select vt.* from visit_task vt where vid = :vid
