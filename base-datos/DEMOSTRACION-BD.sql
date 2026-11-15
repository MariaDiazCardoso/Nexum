-- Ejecutar en Query Tool de nexum_herramientas_local.
-- Solo lectura. Sustituye tu-correo@example.invalid por el correo de tu demostración.

-- 1. Datos del alumno, sin mostrar contraseña ni códigos.
SELECT id, nombre, apellido, email, rol, genero, fecha_nacimiento, email_verificado
FROM public.usuario WHERE email = 'tu-correo@example.invalid';

-- 2. Inscripción, curso y porcentaje guardado.
SELECT u.email, c.titulo AS curso, i.estado, i.progreso_porcentaje
FROM public.inscripcion i
JOIN public.usuario u ON u.id = i.usuario_id
JOIN public.curso c ON c.id = i.curso_id
WHERE u.email = 'tu-correo@example.invalid';

-- 3. Lecciones completadas por ese alumno.
SELECT u.email, l.titulo AS leccion, p.completado, p.fecha_completado
FROM public.progreso_leccion p
JOIN public.usuario u ON u.id = p.usuario_id
JOIN public.leccion l ON l.id = p.leccion_id
WHERE u.email = 'tu-correo@example.invalid'
ORDER BY l.titulo;

-- 4. Intentos y notas calculadas por el backend.
SELECT u.email, l.titulo AS leccion, e.titulo AS evaluacion,
       i.numero_intento, i.puntaje, i.aprobado, i.finalizado_en
FROM public.intento_examen i
JOIN public.usuario u ON u.id = i.usuario_id
JOIN public.examen e ON e.id = i.examen_id
JOIN public.leccion l ON l.id = e.leccion_id
WHERE u.email = 'tu-correo@example.invalid'
ORDER BY l.titulo, i.numero_intento;

-- 5. Certificados emitidos al alumno. Si no terminó, es correcto obtener cero filas.
SELECT c.nombre_alumno, c.titulo_curso, c.fecha_emision, c.codigo_verificacion
FROM public.certificado c
JOIN public.inscripcion i ON i.id = c.inscripcion_id
JOIN public.usuario u ON u.id = i.usuario_id
WHERE u.email = 'tu-correo@example.invalid';

-- 6. Restricciones que protegen la integridad.
SELECT conrelid::regclass AS tabla, conname AS restriccion,
       pg_get_constraintdef(oid) AS definicion
FROM pg_constraint
WHERE connamespace = 'public'::regnamespace
AND conrelid IN ('public.usuario'::regclass, 'public.inscripcion'::regclass,
 'public.progreso_leccion'::regclass, 'public.examen'::regclass,
 'public.intento_examen'::regclass, 'public.certificado'::regclass)
ORDER BY conrelid::regclass::text, conname;

-- 7. Reglas adicionales implementadas con triggers.
SELECT event_object_table AS tabla, trigger_name, event_manipulation
FROM information_schema.triggers
WHERE trigger_schema = 'public'
ORDER BY tabla, trigger_name;
