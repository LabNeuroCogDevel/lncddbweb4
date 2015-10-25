-- name: list-people-by-name
-- list people given a name
select 
  p.*,
  round( (date_part('day',(now()-dob))/365.25)::numeric,1) as age
from person p where lower(concat(fname,' ',lname)) ilike concat('%',lower(:name),'%')
 

-- name: list-people-by-name-study-enroll
-- list people:
-- need: study,etype,hand,fullname,sex,mincount minage maxage
-- p.fname,p.lname,p.dob,
-- :sex, :hand,:etype,:study,:mincount,:minage,:maxage,

select 
 p.*,
 date_part('day',(now()-dob))/365.25 as age,
 max(v.vtimestamp)               as lastvisit,
 count(distinct v.vid)           as numvisits,
 -- array_agg()
 count(distinct e.id)            as ids,
 count(distinct vs.study)        as studies,
 count(distinct v.vtype)         as visittypes
from person p
left join visit v   on v.pid=p.pid
left join enroll e  on e.pid=p.pid
left join visit_study vs on v.vid=vs.vid
where 
  concat(p.fname,' ',p.lname) 
           ilike concat('%',:fullname,'%') and

  p.sex    ilike concat('%',:sex,'%')      and
  p.hand   ilike concat('%',:hand,'%')     and
  e.etype  ilike concat('%',:etype,'%')    and
  vs.study ilike concat('%',:study,'%')    
group by p.pid
having 
  count(distinct v.vid) >= :mincount               and
  date_part('day',(now()-dob))/365.25 >= :minage   and 
  date_part('day',(now()-dob))/365.25 <= :maxage
limit 20
offset :offset


-- name: person-enroll<!
-- give :pid :etype and :id, enroll subject
insert into enroll (pid,etype,id,edate) values (:pid::numeric,:etype,:id::character, date(now()) )

-- name: person-add<!
-- add person, need :fname,:lname,:dob,:sex,:hand,:source
insert into person (fname,lname,dob,sex,hand,adddate,source) values (:fname,:lname,:dob::date,:sex,:hand,date(now()),:source) 
