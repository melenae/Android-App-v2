-- Добавляет в результат app_init() поле user.login из auth.users (email как логин).

create or replace function public.app_init()
returns json
language plpgsql
stable
security definer
as $$
begin
  return json_build_object(
    'user', (
      select (row_to_json(up)::jsonb || jsonb_build_object('login', u.email))::json
      from public.users_profile up
      left join auth.users u on u.id = up.id
      where up.id = auth.uid()
    ),
    'projects', (
      select coalesce(json_agg(p), '[]'::json)
      from public.projects p
      join public.project_members pm on pm.project_id = p.id
      where pm.user_id = auth.uid()
    )
  );
end;
$$;
