-- В projects из app_init добавляется member_role из project_members.role (например PM).

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

    -- member_role: всегда из project_members.role (текст). jsonb_set гарантирует значение поверх to_jsonb(p),
    -- иначе колонка projects.member_role (если есть) при merge || могла «перебить» правую часть в зависимости от порядка ключей.
    'projects', (
      select coalesce(
        json_agg(
          jsonb_set(
            to_jsonb(p),
            '{member_role}',
            coalesce(to_jsonb(pm.role::text), 'null'::jsonb),
            true
          )::json
        ),
        '[]'::json
      )
      from public.projects p
      join public.project_members pm on pm.project_id = p.id
      where pm.user_id = auth.uid()
    ),

    'users', (
      select coalesce(
        json_agg(
          (row_to_json(up)::jsonb || jsonb_build_object('login', u.email))
        ),
        '[]'::json
      )
      from public.users_profile up
      left join auth.users u on u.id = up.id
      where up.id in (
        select distinct pm2.user_id
        from public.project_members pm2
        where pm2.project_id in (
          select pm.project_id
          from public.project_members pm
          where pm.user_id = auth.uid()
        )
      )
    ),

    'accounts', (
      select coalesce(json_agg(a), '[]'::json)
      from public.accounts a
      where a.owner_id = auth.uid()
    )

  );
end;
$$;
