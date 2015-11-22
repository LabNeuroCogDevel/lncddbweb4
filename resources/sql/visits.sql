-- name: list-visits-by-pid
--  need :pid, will give visits with study
select 
v.vid,v.pid,vtype,age,vtimestamp,vscore,visitno,vstatus, vs.study,vs.cohort  
 from visit v join visit_study vs on v.vid=vs.vid
 where v.pid = :pid
 -- group by vid
 order by age desc


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
