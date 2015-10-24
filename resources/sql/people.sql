-- name: list-people-by-name
-- list people given a name
select p.*,date_part('day',(now()-dob))/365.25 as age  from person p where lower(concat(fname,' ',lname)) like concat('%',lower(:name),'%')
 

-- name: list-people-by-name-study-enroll
-- list people:
-- need: study,etype,hand,fullname,sex,mincount minage maxage
select 
 p.*,
 :mincount,:minage,:maxage,
 date_part('day',(now()-dob))/365.25 as curage,
 max(v.vtimestamp)                   as lastvisit,
 count(distinct v.vid)               as numvisits,
 -- array_agg()
 count(distinct e.id)            as ids,
 count(distinct vs.study)        as studies,
 count(distinct v.vtype)         as visittypes
from person p
left join visit v   on v.pid=p.pid
left join enroll e  on e.pid=p.pid
left join visit_study vs on v.vid=vs.vid
where 
  vs.study ilike concat('%',:study,'%')    and
  e.etype  ilike concat('%',:etype,'%')    and
  p.hand   ilike concat('%',:hand,'%')     and
  concat(p.fname,'',p.lname) 
           ilike concat('%',:fullname,'%') and
  p.sex    ilike concat('%',:sex,'%')   
group by p.pid
having 
  count(distinct v.vid) >= :mincount               and
  date_part('day',(now()-dob))/365.25 >= :minage   and 
  date_part('day',(now()-dob))/365.25 <= :maxage
limit 20
offset :offset

