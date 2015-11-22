-- name: list-people-by-name
-- list people given a name
select p.*,date_part('day',(now()-dob))/365.25 as age  from person p where lower(concat(fname,' ',lname)) like concat('%',lower(:name),'%')
 

-- name: list-people-by-name-study-enroll
-- list people:
-- need: study,eid,hand,fullname,sex,mincount minage maxage
select 
 p.*,
 date_part('day',(now()-dob))/365.25 as curage,
 max(v.vtimestamp)                   as lastvisit,
 count(distinct v.vid)               as numvisits,
 count(distinct vs.study)        as nstudies,
 count(distinct d.did)           as ndrops,

 json_agg(distinct e.id)         as ids,
 json_agg(distinct vs.study)     as studies,
 json_agg(distinct v.vtype)      as visittypes,
 max(dc.droplevel)               as maxdrop
from person p
left join visit v   on v.pid=p.pid
left join enroll e  on e.pid=p.pid
left join visit_study vs on v.vid=vs.vid
left join dropped d      on d.pid=p.pid
left join dropcode dc    on dc.dropcode = d.dropcode
where 
  vs.study ilike concat('%',:study,'%')    and
  e.id  ilike concat('%',:eid,'%')    and
  p.hand   ilike concat('%',:hand,'%')     and
  concat(p.fname,' ',p.lname) 
           ilike concat('%',:fullname,'%') and
  p.sex    ilike concat('%',:sex,'%')   
group by p.pid
having 
  count(distinct v.vid) >= :mincount               and
  date_part('day',(now()-dob))/365.25 >= :minage   and 
  date_part('day',(now()-dob))/365.25 <= :maxage
order by lastvisit desc
limit 10
offset :offset


-- name: list-people-all-notes
-- all notes a person has
select n.* from person p 
  join note n on p.pid=n.pid
where pid = :pid

-- name: list-people-notes
-- only notes that are not assocated with a visit
select n.*
  from person p 
  join note n on p.pid=n.pid 
  left join visit_note vn on vn.nid=n.nid
  where
    vn.vid is null and 
    pid = :pid 
