-- name: list-visits-by-pid
--  need :pid, will give visits with study
select 
 round(v.age::numeric,1) as age, 
 v.vstatus, v.vscore, v.visitno,  v.vid,  v.pid,  v.vtype,  v.googleuri,  v.vtimestamp, 
 vs.* 
from visit v 
 join visit_study vs on vs.vid = v.vid
where v.pid = :pid
 -- group by vid
order by v.age desc

-- name: list-visit_notes
--  need :vid, will give visit notes
select n.* from note n 
 join visit_note vn on vn.nid=n.nid 
 where vid = :vid

-- name: list-visit_tasks
--  need :vid, will give visit tasks
select vt.* from visit_task vt where vid = :vid
