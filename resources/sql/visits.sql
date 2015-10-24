-- name: list-visits-by-pid
--  need :pid, will give visits with study
select 
v.*, vs.* 
 from visit v 
 join visit_study vs on vs.vid = v.vid
 where v.pid = :pid
 -- group by vid
 order by age desc
