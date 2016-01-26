-- name: list-studies
select distinct study from study

-- name:  list-cohorts
select distinct cohort from visit_study

-- name: list-visittypes
select distinct vtype from visit

-- name: list-tasks
select study,task,modes from task natural join study_task

-- name: list-etypes
select etype from enroll group by etype order by count(etype) desc

--name: list-newest
select max(id::float)+1 as id from enroll where etype ilike :etype 

