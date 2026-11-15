--
-- PostgreSQL database dump
--

\restrict HBOLt1E1kZbWo8W84V0iSklnbD46QWtBnumMUo9Jz3M1GPNbDdcbEeexiR0bgeh

-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.6

-- Started on 2026-10-25 18:04:04

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 3 (class 3079 OID 35732)
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- TOC entry 5478 (class 0 OID 0)
-- Dependencies: 3
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- TOC entry 2 (class 3079 OID 35721)
-- Name: uuid-ossp; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;


--
-- TOC entry 5479 (class 0 OID 0)
-- Dependencies: 2
-- Name: EXTENSION "uuid-ossp"; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION "uuid-ossp" IS 'generate universally unique identifiers (UUIDs)';


--
-- TOC entry 324 (class 1255 OID 39018)
-- Name: exigir_evaluacion_progreso(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.exigir_evaluacion_progreso() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF NEW.completado AND EXISTS(SELECT FROM public.examen e WHERE e.leccion_id=NEW.leccion_id AND e.publicado AND NOT EXISTS(
  SELECT FROM public.intento_examen i WHERE i.examen_id=e.id AND i.usuario_id=NEW.usuario_id AND i.aprobado AND i.finalizado_en IS NOT NULL)) THEN
  RAISE EXCEPTION 'Se requiere aprobar la evaluacion de la leccion' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.exigir_evaluacion_progreso() OWNER TO postgres;

--
-- TOC entry 310 (class 1255 OID 39020)
-- Name: exigir_evaluaciones_certificado(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.exigir_evaluaciones_certificado() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE uid uuid; cid uuid;
BEGIN
 SELECT usuario_id,curso_id INTO uid,cid FROM public.inscripcion WHERE id=NEW.inscripcion_id;
 IF EXISTS(SELECT FROM public.examen e JOIN public.leccion l ON l.id=e.leccion_id JOIN public.modulo m ON m.id=l.modulo_id
  WHERE m.curso_id=cid AND e.publicado AND NOT EXISTS(SELECT FROM public.intento_examen i WHERE i.examen_id=e.id AND i.usuario_id=uid AND i.aprobado AND i.finalizado_en IS NOT NULL)) THEN
  RAISE EXCEPTION 'El certificado requiere aprobar las evaluaciones publicadas' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.exigir_evaluaciones_certificado() OWNER TO postgres;

--
-- TOC entry 265 (class 1255 OID 38130)
-- Name: exigir_genero_usuario(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.exigir_genero_usuario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.genero IS NULL THEN
        IF TG_OP = 'INSERT' THEN
            RAISE EXCEPTION 'El género es obligatorio para registrar un usuario'
                USING ERRCODE = '23502', COLUMN = 'genero', TABLE = 'usuario';
        ELSIF OLD.genero IS NOT NULL THEN
            RAISE EXCEPTION 'No se puede eliminar el género registrado'
                USING ERRCODE = '23502', COLUMN = 'genero', TABLE = 'usuario';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.exigir_genero_usuario() OWNER TO postgres;

--
-- TOC entry 329 (class 1255 OID 38978)
-- Name: normalizar_email_usuario(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.normalizar_email_usuario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 NEW.email := lower(btrim(NEW.email));
 IF TG_OP='UPDATE' AND NEW.email IS DISTINCT FROM OLD.email THEN
  NEW.email_verificado := false;
  UPDATE public.codigo_verificacion SET usado=true WHERE usuario_id=NEW.id AND upper(tipo) IN ('EMAIL','VERIFICACION_EMAIL','VERIFICACION_CORREO');
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.normalizar_email_usuario() OWNER TO postgres;

--
-- TOC entry 281 (class 1255 OID 39026)
-- Name: preservar_datos_certificado(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.preservar_datos_certificado() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.nombre_alumno IS NULL OR NEW.titulo_curso IS NULL THEN
   SELECT trim(u.nombre||' '||u.apellido),cu.titulo INTO NEW.nombre_alumno,NEW.titulo_curso FROM public.inscripcion i JOIN public.usuario u ON u.id=i.usuario_id JOIN public.curso cu ON cu.id=i.curso_id WHERE i.id=NEW.inscripcion_id;
  END IF;
 ELSE
  IF NEW.inscripcion_id IS DISTINCT FROM OLD.inscripcion_id OR NEW.codigo_verificacion IS DISTINCT FROM OLD.codigo_verificacion OR NEW.fecha_emision IS DISTINCT FROM OLD.fecha_emision OR NEW.nombre_alumno IS DISTINCT FROM OLD.nombre_alumno OR NEW.titulo_curso IS DISTINCT FROM OLD.titulo_curso THEN
   RAISE EXCEPTION 'Los datos de un certificado emitido deben conservarse' USING ERRCODE='23514';
  END IF;
 END IF;
 IF trim(NEW.nombre_alumno)='' OR trim(NEW.titulo_curso)='' THEN RAISE EXCEPTION 'El certificado requiere alumno y curso' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.preservar_datos_certificado() OWNER TO postgres;

--
-- TOC entry 299 (class 1255 OID 39013)
-- Name: proteger_configuracion_examen(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.proteger_configuracion_examen() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF TG_OP='UPDATE' AND EXISTS(SELECT FROM public.intento_examen WHERE examen_id=OLD.id) AND
 (NEW.leccion_id IS DISTINCT FROM OLD.leccion_id OR (NEW.intentos_maximos IS DISTINCT FROM OLD.intentos_maximos AND (OLD.intentos_maximos IS NULL OR (NEW.intentos_maximos IS NOT NULL AND NEW.intentos_maximos<OLD.intentos_maximos))) OR NEW.puntaje_minimo_aprobacion IS DISTINCT FROM OLD.puntaje_minimo_aprobacion OR NEW.publicado IS DISTINCT FROM OLD.publicado OR NEW.titulo IS DISTINCT FROM OLD.titulo) THEN
 RAISE EXCEPTION 'La evaluacion tiene intentos y no puede modificarse' USING ERRCODE='23514'; END IF;
 IF NEW.publicado AND (TG_OP='INSERT' OR NOT OLD.publicado) THEN
  IF NOT EXISTS(SELECT FROM public.pregunta WHERE examen_id=NEW.id) OR EXISTS(
    SELECT FROM public.pregunta p LEFT JOIN public.opcion_respuesta o ON o.pregunta_id=p.id WHERE p.examen_id=NEW.id GROUP BY p.id
    HAVING count(o.id)<2 OR count(o.id) FILTER(WHERE o.es_correcta)<>1) THEN
   RAISE EXCEPTION 'Cada pregunta necesita al menos dos opciones y una unica respuesta correcta' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.proteger_configuracion_examen() OWNER TO postgres;

--
-- TOC entry 302 (class 1255 OID 39015)
-- Name: proteger_preguntas_publicadas(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.proteger_preguntas_publicadas() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE eid uuid; pid uuid; publicado boolean;
BEGIN
 IF TG_TABLE_NAME='pregunta' THEN
  IF TG_OP='UPDATE' AND OLD.examen_id IS DISTINCT FROM NEW.examen_id THEN RAISE EXCEPTION 'No se puede mover una pregunta a otro examen' USING ERRCODE='23514'; END IF;
  IF TG_OP='DELETE' THEN eid:=OLD.examen_id; ELSE eid:=NEW.examen_id; END IF;
 ELSE
  IF TG_OP='UPDATE' AND OLD.pregunta_id IS DISTINCT FROM NEW.pregunta_id THEN RAISE EXCEPTION 'No se puede mover una opcion a otra pregunta' USING ERRCODE='23514'; END IF;
  IF TG_OP='DELETE' THEN pid:=OLD.pregunta_id; ELSE pid:=NEW.pregunta_id; END IF;
  SELECT examen_id INTO eid FROM public.pregunta WHERE id=pid;
 END IF;
 SELECT e.publicado INTO publicado FROM public.examen e WHERE e.id=eid FOR UPDATE;
 IF publicado OR EXISTS(SELECT FROM public.intento_examen WHERE examen_id=eid) THEN
  RAISE EXCEPTION 'No se pueden modificar preguntas de una evaluacion publicada o con intentos' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;


ALTER FUNCTION public.proteger_preguntas_publicadas() OWNER TO postgres;

--
-- TOC entry 332 (class 1255 OID 38999)
-- Name: proteger_relaciones_evaluacion(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.proteger_relaciones_evaluacion() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF TG_TABLE_NAME='pregunta' THEN
  IF NEW.examen_id IS DISTINCT FROM OLD.examen_id AND EXISTS(SELECT FROM public.respuesta_intento WHERE pregunta_id=OLD.id) THEN
   RAISE EXCEPTION 'No se puede mover una pregunta que ya fue respondida' USING ERRCODE='23514';
  END IF;
 ELSE
  IF NEW.pregunta_id IS DISTINCT FROM OLD.pregunta_id AND EXISTS(SELECT FROM public.respuesta_intento WHERE opcion_id=OLD.id) THEN
   RAISE EXCEPTION 'No se puede mover una opción que ya fue respondida' USING ERRCODE='23514';
  END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.proteger_relaciones_evaluacion() OWNER TO postgres;

--
-- TOC entry 293 (class 1255 OID 39024)
-- Name: proteger_respuestas_finalizadas(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.proteger_respuestas_finalizadas() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE iid uuid;
BEGIN
 IF TG_OP='DELETE' THEN iid:=OLD.intento_id; ELSE iid:=NEW.intento_id; END IF;
 PERFORM 1 FROM public.intento_examen WHERE id=iid AND finalizado_en IS NOT NULL;
 IF FOUND THEN RAISE EXCEPTION 'No se pueden cambiar respuestas de un intento finalizado' USING ERRCODE='23514'; END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 SELECT es_correcta INTO NEW.es_correcta FROM public.opcion_respuesta WHERE id=NEW.opcion_id;
 RETURN NEW;
END $$;


ALTER FUNCTION public.proteger_respuestas_finalizadas() OWNER TO postgres;

--
-- TOC entry 330 (class 1255 OID 39004)
-- Name: proteger_suscripcion_consumida(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.proteger_suscripcion_consumida() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
 IF NEW.creditos_asignados < (SELECT coalesce(sum(creditos_usados),0) FROM public.consumo_ia WHERE suscripcion_id=NEW.id) THEN
  RAISE EXCEPTION 'No se pueden asignar menos créditos que los ya consumidos' USING ERRCODE='23514';
 END IF;
 IF NEW.usuario_id IS DISTINCT FROM OLD.usuario_id AND (EXISTS(SELECT FROM public.consumo_ia WHERE suscripcion_id=NEW.id) OR EXISTS(SELECT FROM public.pago WHERE suscripcion_id=NEW.id)) THEN
  RAISE EXCEPTION 'No se puede reasignar una suscripción con pagos o consumos' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.proteger_suscripcion_consumida() OWNER TO postgres;

--
-- TOC entry 333 (class 1255 OID 38995)
-- Name: validar_calificacion_curso(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_calificacion_curso() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE curso_leccion uuid;
BEGIN
 IF NEW.leccion_id IS NOT NULL THEN
  SELECT m.curso_id INTO curso_leccion FROM public.leccion l JOIN public.modulo m ON m.id=l.modulo_id WHERE l.id=NEW.leccion_id;
  IF curso_leccion IS DISTINCT FROM NEW.curso_id THEN RAISE EXCEPTION 'La lección no pertenece al curso de la calificación' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_calificacion_curso() OWNER TO postgres;

--
-- TOC entry 258 (class 1255 OID 38989)
-- Name: validar_certificado_completo(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_certificado_completo() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE i public.inscripcion%ROWTYPE; total bigint; completas bigint;
BEGIN
 SELECT * INTO STRICT i FROM public.inscripcion WHERE id=NEW.inscripcion_id FOR SHARE;
 SELECT count(*),count(p.id) FILTER (WHERE p.completado) INTO total,completas FROM public.leccion l JOIN public.modulo m ON m.id=l.modulo_id LEFT JOIN public.progreso_leccion p ON p.leccion_id=l.id AND p.usuario_id=i.usuario_id WHERE m.curso_id=i.curso_id;
 IF i.estado<>'COMPLETADA' OR i.progreso_porcentaje<>100 OR total=0 OR completas<>total THEN RAISE EXCEPTION 'El certificado requiere inscripción completada y todas las lecciones finalizadas' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_certificado_completo() OWNER TO postgres;

--
-- TOC entry 266 (class 1255 OID 39022)
-- Name: validar_cierre_evaluacion(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_cierre_evaluacion() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE limite numeric; total integer; respondidas integer; correctas integer; nota numeric;
BEGIN
 SELECT COALESCE(puntaje_minimo_aprobacion,70) INTO limite FROM public.examen WHERE id=NEW.examen_id AND publicado;
 IF NOT FOUND THEN RAISE EXCEPTION 'La evaluacion no esta publicada' USING ERRCODE='23514'; END IF;
 IF TG_OP='INSERT' AND (NEW.finalizado_en IS NOT NULL OR NEW.puntaje IS NOT NULL OR NEW.aprobado IS NOT NULL) THEN
  RAISE EXCEPTION 'Inicia el intento antes de enviar respuestas' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND OLD.finalizado_en IS NOT NULL THEN
  RAISE EXCEPTION 'El intento ya fue finalizado' USING ERRCODE='23514'; END IF;
 IF NEW.finalizado_en IS NULL AND (NEW.puntaje IS NOT NULL OR NEW.aprobado IS NOT NULL) THEN RAISE EXCEPTION 'El resultado se guarda al finalizar el intento' USING ERRCODE='23514'; END IF;
 IF NEW.finalizado_en IS NOT NULL THEN
  SELECT count(*) INTO total FROM public.pregunta WHERE examen_id=NEW.examen_id;
  SELECT count(*),count(*) FILTER(WHERE o.es_correcta) INTO respondidas,correctas FROM public.respuesta_intento r JOIN public.opcion_respuesta o ON o.id=r.opcion_id WHERE r.intento_id=NEW.id;
  IF total=0 OR respondidas<>total THEN RAISE EXCEPTION 'Responde todas las preguntas antes de finalizar' USING ERRCODE='23514'; END IF;
  nota:=round(correctas*100.0/total,2);
  IF NEW.puntaje IS DISTINCT FROM nota OR NEW.aprobado IS DISTINCT FROM (nota>=limite) THEN RAISE EXCEPTION 'El resultado no corresponde a las respuestas' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_cierre_evaluacion() OWNER TO postgres;

--
-- TOC entry 312 (class 1255 OID 38997)
-- Name: validar_consumo_propietario(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_consumo_propietario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE propietario uuid; limite integer; usados bigint; estado_plan text; inicio date; fin date;
BEGIN
 SELECT usuario_id,creditos_asignados,estado,fecha_inicio,fecha_fin INTO propietario,limite,estado_plan,inicio,fin FROM public.suscripcion_ia WHERE id=NEW.suscripcion_id FOR UPDATE;
 IF propietario IS DISTINCT FROM NEW.usuario_id OR lower(estado_plan)<>'activa' OR inicio>CURRENT_DATE OR (fin IS NOT NULL AND fin<CURRENT_DATE) THEN RAISE EXCEPTION 'La suscripción no está vigente o pertenece a otro usuario' USING ERRCODE='23514'; END IF;
 IF NEW.mensaje_id IS NOT NULL AND NOT EXISTS (SELECT FROM public.ia_mensaje m JOIN public.ia_conversacion c ON c.id=m.conversacion_id WHERE m.id=NEW.mensaje_id AND c.usuario_id=NEW.usuario_id) THEN RAISE EXCEPTION 'El mensaje pertenece a otro usuario' USING ERRCODE='23514'; END IF;
 SELECT coalesce(sum(creditos_usados),0) INTO usados FROM public.consumo_ia WHERE suscripcion_id=NEW.suscripcion_id AND id<>NEW.id;
 IF usados+NEW.creditos_usados>limite THEN RAISE EXCEPTION 'Créditos de IA insuficientes' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_consumo_propietario() OWNER TO postgres;

--
-- TOC entry 271 (class 1255 OID 39002)
-- Name: validar_contexto_ia(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_contexto_ia() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE curso_leccion uuid;
BEGIN
 IF NEW.curso_id IS NOT NULL AND NEW.leccion_id IS NOT NULL THEN
  SELECT m.curso_id INTO curso_leccion FROM public.leccion l JOIN public.modulo m ON m.id=l.modulo_id WHERE l.id=NEW.leccion_id;
  IF curso_leccion IS DISTINCT FROM NEW.curso_id THEN RAISE EXCEPTION 'El contexto de IA contiene una lección de otro curso' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_contexto_ia() OWNER TO postgres;

--
-- TOC entry 269 (class 1255 OID 38128)
-- Name: validar_fecha_nacimiento_usuario(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_fecha_nacimiento_usuario() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.fecha_nacimiento IS NULL THEN
        IF TG_OP = 'INSERT' THEN
            RAISE EXCEPTION 'La fecha de nacimiento es obligatoria para registrar un usuario'
                USING ERRCODE = '23502', COLUMN = 'fecha_nacimiento', TABLE = 'usuario';
        ELSIF OLD.fecha_nacimiento IS NOT NULL THEN
            RAISE EXCEPTION 'No se puede eliminar la fecha de nacimiento registrada'
                USING ERRCODE = '23502', COLUMN = 'fecha_nacimiento', TABLE = 'usuario';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.validar_fecha_nacimiento_usuario() OWNER TO postgres;

--
-- TOC entry 325 (class 1255 OID 38982)
-- Name: validar_inscripcion_negocio(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_inscripcion_negocio() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE c public.curso%ROWTYPE; p public.pago%ROWTYPE; u public.usuario%ROWTYPE;
BEGIN
 SELECT * INTO STRICT c FROM public.curso WHERE id=NEW.curso_id FOR SHARE;
 IF TG_OP='INSERT' THEN
  SELECT * INTO STRICT u FROM public.usuario WHERE id=NEW.usuario_id FOR SHARE;
  IF NOT u.email_verificado OR u.deleted_at IS NOT NULL THEN RAISE EXCEPTION 'Se requiere una cuenta activa con correo verificado' USING ERRCODE='23514'; END IF;
  IF c.estado<>'PUBLICADO' OR c.deleted_at IS NOT NULL THEN RAISE EXCEPTION 'El curso no está publicado' USING ERRCODE='23514'; END IF;
 END IF;
 IF NEW.pago_id IS NOT NULL THEN
  SELECT * INTO STRICT p FROM public.pago WHERE id=NEW.pago_id FOR SHARE;
  IF p.usuario_id<>NEW.usuario_id OR p.curso_id IS DISTINCT FROM NEW.curso_id THEN RAISE EXCEPTION 'El pago no corresponde al usuario y curso' USING ERRCODE='23514'; END IF;
 END IF;
 IF c.precio>0 AND NEW.estado IN ('ACTIVA','COMPLETADA') THEN
  IF NEW.pago_id IS NULL OR upper(p.estado) NOT IN ('COMPLETADO','APROBADO','PAGADO') THEN RAISE EXCEPTION 'El curso requiere un pago aprobado' USING ERRCODE='23514'; END IF;
  IF TG_OP='INSERT' THEN
   IF p.monto<c.precio OR p.moneda<>c.moneda THEN RAISE EXCEPTION 'El importe o moneda no corresponde al curso' USING ERRCODE='23514'; END IF;
  END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_inscripcion_negocio() OWNER TO postgres;

--
-- TOC entry 262 (class 1255 OID 38991)
-- Name: validar_intento_examen(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_intento_examen() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE maximo integer; curso_examen uuid;
BEGIN
 SELECT e.intentos_maximos,m.curso_id INTO maximo,curso_examen FROM public.examen e JOIN public.leccion l ON l.id=e.leccion_id JOIN public.modulo m ON m.id=l.modulo_id WHERE e.id=NEW.examen_id FOR SHARE OF e;
 IF NOT EXISTS(SELECT FROM public.pregunta WHERE examen_id=NEW.examen_id) OR EXISTS(
  SELECT FROM public.pregunta p LEFT JOIN public.opcion_respuesta o ON o.pregunta_id=p.id WHERE p.examen_id=NEW.examen_id GROUP BY p.id HAVING count(o.id)<2 OR count(o.id) FILTER(WHERE o.es_correcta)=0
 ) THEN RAISE EXCEPTION 'El examen requiere preguntas con al menos dos opciones y una correcta' USING ERRCODE='23514'; END IF;
 IF maximo IS NOT NULL AND NEW.numero_intento>maximo THEN RAISE EXCEPTION 'Se superó el máximo de intentos' USING ERRCODE='23514'; END IF;
 IF NOT EXISTS(SELECT FROM public.inscripcion WHERE usuario_id=NEW.usuario_id AND curso_id=curso_examen AND estado IN ('ACTIVA','COMPLETADA')) THEN RAISE EXCEPTION 'El examen requiere inscripción vigente' USING ERRCODE='23514'; END IF;
 IF TG_OP='UPDATE' AND (OLD.examen_id IS DISTINCT FROM NEW.examen_id OR OLD.usuario_id IS DISTINCT FROM NEW.usuario_id) THEN RAISE EXCEPTION 'No se puede reasignar un intento a otro examen o usuario' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_intento_examen() OWNER TO postgres;

--
-- TOC entry 316 (class 1255 OID 38984)
-- Name: validar_pago_negocio(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_pago_negocio() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE propietario uuid;
BEGIN
 IF NEW.suscripcion_id IS NOT NULL THEN
  SELECT usuario_id INTO propietario FROM public.suscripcion_ia WHERE id=NEW.suscripcion_id FOR SHARE;
  IF propietario IS DISTINCT FROM NEW.usuario_id THEN RAISE EXCEPTION 'La suscripción pertenece a otro usuario' USING ERRCODE='23514'; END IF;
 END IF;
 IF TG_OP='UPDATE' AND (NEW.monto IS DISTINCT FROM OLD.monto OR NEW.moneda IS DISTINCT FROM OLD.moneda) AND EXISTS(SELECT FROM public.inscripcion WHERE pago_id=NEW.id AND estado IN ('ACTIVA','COMPLETADA')) THEN
  RAISE EXCEPTION 'No se puede modificar el importe de un pago que habilita una inscripción vigente' USING ERRCODE='23514';
 END IF;
 IF TG_OP='UPDATE' AND EXISTS (SELECT FROM public.inscripcion i WHERE i.pago_id=NEW.id AND (i.usuario_id<>NEW.usuario_id OR i.curso_id IS DISTINCT FROM NEW.curso_id OR (i.estado IN ('ACTIVA','COMPLETADA') AND upper(NEW.estado) NOT IN ('COMPLETADO','APROBADO','PAGADO')))) THEN
  RAISE EXCEPTION 'Cancele primero las inscripciones antes de modificar o revertir su pago' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_pago_negocio() OWNER TO postgres;

--
-- TOC entry 272 (class 1255 OID 38986)
-- Name: validar_progreso_inscrito(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_progreso_inscrito() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE curso_leccion uuid;
BEGIN
 SELECT m.curso_id INTO curso_leccion FROM public.leccion l JOIN public.modulo m ON m.id=l.modulo_id WHERE l.id=NEW.leccion_id;
 PERFORM 1 FROM public.inscripcion WHERE usuario_id=NEW.usuario_id AND curso_id=curso_leccion AND estado IN ('ACTIVA','COMPLETADA') FOR SHARE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Se requiere inscripción vigente en el curso de la lección' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_progreso_inscrito() OWNER TO postgres;

--
-- TOC entry 290 (class 1255 OID 38993)
-- Name: validar_respuesta_examen(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.validar_respuesta_examen() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE examen_intento uuid; examen_pregunta uuid; pregunta_opcion uuid;
BEGIN
 SELECT examen_id INTO examen_intento FROM public.intento_examen WHERE id=NEW.intento_id FOR SHARE;
 SELECT examen_id INTO examen_pregunta FROM public.pregunta WHERE id=NEW.pregunta_id FOR SHARE;
 IF examen_intento IS DISTINCT FROM examen_pregunta THEN RAISE EXCEPTION 'La pregunta no pertenece al examen del intento' USING ERRCODE='23514'; END IF;
 IF NEW.opcion_id IS NOT NULL THEN
  SELECT pregunta_id INTO pregunta_opcion FROM public.opcion_respuesta WHERE id=NEW.opcion_id FOR SHARE;
  IF pregunta_opcion IS DISTINCT FROM NEW.pregunta_id THEN RAISE EXCEPTION 'La opción no pertenece a la pregunta' USING ERRCODE='23514'; END IF;
 END IF;
 RETURN NEW;
END $$;


ALTER FUNCTION public.validar_respuesta_examen() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 229 (class 1259 OID 37627)
-- Name: actividad; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.actividad (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    leccion_id uuid NOT NULL,
    titulo character varying(255) NOT NULL,
    tipo character varying(30),
    instrucciones text,
    xp_otorgada integer,
    CONSTRAINT ck_nexum_actividad CHECK (((xp_otorgada IS NULL) OR (xp_otorgada >= 0))),
    CONSTRAINT ck_texto_actividad CHECK (((titulo)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.actividad OWNER TO postgres;

--
-- TOC entry 236 (class 1259 OID 37740)
-- Name: calificacion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.calificacion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    curso_id uuid NOT NULL,
    leccion_id uuid,
    intento_examen_id uuid,
    entrega_actividad_id uuid,
    tipo character varying(30) NOT NULL,
    titulo character varying(255) NOT NULL,
    nota numeric(5,2) NOT NULL,
    nota_maxima numeric(5,2) NOT NULL,
    peso numeric(5,2),
    comentario text,
    fecha_registro timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_nota_valida CHECK ((nota <= nota_maxima)),
    CONSTRAINT ck_nexum_calificacion CHECK (((nota >= (0)::numeric) AND (nota_maxima > (0)::numeric) AND ((peso IS NULL) OR (peso >= (0)::numeric)))),
    CONSTRAINT ck_texto_calificacion CHECK ((((tipo)::text !~ '^[[:space:]]*$'::text) AND ((titulo)::text !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.calificacion OWNER TO postgres;

--
-- TOC entry 221 (class 1259 OID 37513)
-- Name: categoria; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.categoria (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(100) NOT NULL,
    icono_url character varying(255),
    CONSTRAINT ck_texto_categoria CHECK (((nombre)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.categoria OWNER TO postgres;

--
-- TOC entry 241 (class 1259 OID 37865)
-- Name: certificado; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.certificado (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    inscripcion_id uuid NOT NULL,
    codigo_verificacion character varying(100) NOT NULL,
    url_pdf text,
    fecha_emision timestamp without time zone DEFAULT now() NOT NULL,
    nombre_alumno character varying(255) NOT NULL,
    titulo_curso character varying(255) NOT NULL,
    CONSTRAINT ck_texto_certificado CHECK (((codigo_verificacion)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.certificado OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 37497)
-- Name: codigo_verificacion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.codigo_verificacion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    tipo character varying(30) NOT NULL,
    codigo_hash text NOT NULL,
    usado boolean DEFAULT false NOT NULL,
    expira_en timestamp without time zone NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_nexum_codigo_verificacion CHECK ((expira_en > fecha_creacion)),
    CONSTRAINT ck_texto_codigo_verificacion CHECK ((((tipo)::text !~ '^[[:space:]]*$'::text) AND (codigo_hash !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.codigo_verificacion OWNER TO postgres;

--
-- TOC entry 242 (class 1259 OID 37883)
-- Name: comentario_leccion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.comentario_leccion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    leccion_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    comentario_padre_id uuid,
    contenido text NOT NULL,
    fecha timestamp without time zone DEFAULT now() NOT NULL,
    deleted_at timestamp without time zone,
    CONSTRAINT ck_texto_comentario_leccion CHECK ((contenido !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.comentario_leccion OWNER TO postgres;

--
-- TOC entry 251 (class 1259 OID 38028)
-- Name: consumo_ia; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.consumo_ia (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    suscripcion_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    mensaje_id uuid,
    creditos_usados integer NOT NULL,
    fecha timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_nexum_consumo_ia CHECK ((creditos_usados > 0))
);


ALTER TABLE public.consumo_ia OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 37519)
-- Name: curso; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.curso (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    responsable_id uuid NOT NULL,
    titulo character varying(255) NOT NULL,
    slug character varying(255),
    descripcion text,
    recomendado_para text,
    estado character varying(20) DEFAULT 'BORRADOR'::character varying NOT NULL,
    nivel character varying(20),
    duracion_estimada character varying(50),
    ruta character varying(50),
    categoria_academica character varying(80),
    precio numeric(10,2) DEFAULT 0 NOT NULL,
    moneda character varying(3) DEFAULT 'PEN'::character varying NOT NULL,
    xp_otorgada integer,
    promedio_valoracion numeric(3,2),
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone,
    deleted_at timestamp without time zone,
    portada_url character varying(255),
    CONSTRAINT ck_curso_portada_local CHECK (((portada_url IS NULL) OR ((portada_url)::text ~ '^/api/cursos/[0-9a-f-]{36}/portada/[0-9a-f-]{36}[.]jpg$'::text))),
    CONSTRAINT ck_nexum_curso CHECK (((precio >= (0)::numeric) AND ((xp_otorgada IS NULL) OR (xp_otorgada >= 0)) AND ((promedio_valoracion IS NULL) OR ((promedio_valoracion >= (0)::numeric) AND (promedio_valoracion <= (5)::numeric))) AND ((estado)::text = ANY ((ARRAY['BORRADOR'::character varying, 'REVISION'::character varying, 'PUBLICADO'::character varying, 'ARCHIVADO'::character varying])::text[])) AND ((moneda)::text ~ '^[A-Z]{3}$'::text))),
    CONSTRAINT ck_texto_curso CHECK ((((titulo)::text !~ '^[[:space:]]*$'::text) AND ((estado)::text !~ '^[[:space:]]*$'::text) AND ((moneda)::text !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.curso OWNER TO postgres;

--
-- TOC entry 223 (class 1259 OID 37540)
-- Name: curso_categoria; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.curso_categoria (
    curso_id uuid NOT NULL,
    categoria_id uuid NOT NULL
);


ALTER TABLE public.curso_categoria OWNER TO postgres;

--
-- TOC entry 230 (class 1259 OID 37640)
-- Name: entrega_actividad; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.entrega_actividad (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    actividad_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    contenido text,
    url_archivo text,
    estado character varying(20) NOT NULL,
    fecha_entrega timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_texto_entrega_actividad CHECK (((estado)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.entrega_actividad OWNER TO postgres;

--
-- TOC entry 231 (class 1259 OID 37660)
-- Name: examen; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.examen (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    leccion_id uuid NOT NULL,
    titulo character varying(255) NOT NULL,
    intentos_maximos integer,
    puntaje_minimo_aprobacion numeric(5,2),
    xp_otorgada integer,
    publicado boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_examen_porcentaje CHECK (((puntaje_minimo_aprobacion IS NULL) OR ((puntaje_minimo_aprobacion >= (0)::numeric) AND (puntaje_minimo_aprobacion <= (100)::numeric)))),
    CONSTRAINT ck_nexum_examen CHECK ((((intentos_maximos IS NULL) OR (intentos_maximos >= 1)) AND ((puntaje_minimo_aprobacion IS NULL) OR (puntaje_minimo_aprobacion >= (0)::numeric)) AND ((xp_otorgada IS NULL) OR (xp_otorgada >= 0)))),
    CONSTRAINT ck_texto_examen CHECK (((titulo)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.examen OWNER TO postgres;

--
-- TOC entry 255 (class 1259 OID 38100)
-- Name: ia_analisis_leccion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ia_analisis_leccion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    leccion_id uuid NOT NULL,
    nivel character varying(30),
    duracion character varying(50),
    resumen text,
    consejo text,
    siguiente_paso text,
    objetivos jsonb,
    proyecto jsonb,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.ia_analisis_leccion OWNER TO postgres;

--
-- TOC entry 252 (class 1259 OID 38046)
-- Name: ia_conversacion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ia_conversacion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    titulo character varying(150),
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.ia_conversacion OWNER TO postgres;

--
-- TOC entry 254 (class 1259 OID 38079)
-- Name: ia_conversacion_contexto; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ia_conversacion_contexto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    conversacion_id uuid NOT NULL,
    curso_id uuid,
    leccion_id uuid
);


ALTER TABLE public.ia_conversacion_contexto OWNER TO postgres;

--
-- TOC entry 253 (class 1259 OID 38059)
-- Name: ia_mensaje; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.ia_mensaje (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    conversacion_id uuid NOT NULL,
    rol character varying(20) NOT NULL,
    mensaje text NOT NULL,
    acciones jsonb,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_texto_ia_mensaje CHECK ((((rol)::text !~ '^[[:space:]]*$'::text) AND (mensaje !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.ia_mensaje OWNER TO postgres;

--
-- TOC entry 238 (class 1259 OID 37795)
-- Name: inscripcion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.inscripcion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    curso_id uuid NOT NULL,
    pago_id uuid,
    estado character varying(20) DEFAULT 'ACTIVA'::character varying NOT NULL,
    progreso_porcentaje numeric(5,2) DEFAULT 0 NOT NULL,
    fecha_inscripcion timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_nexum_inscripcion CHECK ((((progreso_porcentaje >= (0)::numeric) AND (progreso_porcentaje <= (100)::numeric)) AND ((estado)::text = ANY ((ARRAY['ACTIVA'::character varying, 'COMPLETADA'::character varying, 'CANCELADA'::character varying])::text[])) AND (((estado)::text <> 'COMPLETADA'::text) OR (progreso_porcentaje = (100)::numeric)))),
    CONSTRAINT ck_texto_inscripcion CHECK (((estado)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.inscripcion OWNER TO postgres;

--
-- TOC entry 234 (class 1259 OID 37699)
-- Name: intento_examen; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.intento_examen (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    examen_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    numero_intento integer NOT NULL,
    puntaje numeric(5,2),
    aprobado boolean,
    iniciado_en timestamp without time zone DEFAULT now() NOT NULL,
    finalizado_en timestamp without time zone,
    CONSTRAINT ck_nexum_intento_examen CHECK (((numero_intento >= 1) AND ((puntaje IS NULL) OR (puntaje >= (0)::numeric)) AND ((finalizado_en IS NULL) OR (finalizado_en >= iniciado_en))))
);


ALTER TABLE public.intento_examen OWNER TO postgres;

--
-- TOC entry 225 (class 1259 OID 37570)
-- Name: leccion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.leccion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    modulo_id uuid NOT NULL,
    titulo character varying(255) NOT NULL,
    slug character varying(180),
    orden integer NOT NULL,
    tipo character varying(20),
    dificultad character varying(20),
    duracion_estimada character varying(30),
    duracion_minima integer,
    contenido_texto text,
    puntos_otorgados integer,
    xp_otorgada integer,
    video_url text,
    imagen_url text,
    reto_practico text,
    CONSTRAINT ck_nexum_leccion CHECK (((orden >= 1) AND ((duracion_minima IS NULL) OR (duracion_minima >= 0)) AND ((puntos_otorgados IS NULL) OR (puntos_otorgados >= 0)) AND ((xp_otorgada IS NULL) OR (xp_otorgada >= 0)))),
    CONSTRAINT ck_texto_leccion CHECK (((titulo)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.leccion OWNER TO postgres;

--
-- TOC entry 244 (class 1259 OID 37930)
-- Name: medalla; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.medalla (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion text,
    imagen_url text,
    criterio_tipo character varying(30),
    criterio_valor integer,
    CONSTRAINT ck_texto_medalla CHECK (((nombre)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.medalla OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 37555)
-- Name: modulo; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.modulo (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    curso_id uuid NOT NULL,
    titulo character varying(255) NOT NULL,
    slug character varying(180),
    descripcion text,
    orden integer NOT NULL,
    xp_otorgada integer,
    CONSTRAINT ck_nexum_modulo CHECK (((orden >= 1) AND ((xp_otorgada IS NULL) OR (xp_otorgada >= 0)))),
    CONSTRAINT ck_texto_modulo CHECK (((titulo)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.modulo OWNER TO postgres;

--
-- TOC entry 248 (class 1259 OID 37980)
-- Name: movimiento_xp; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.movimiento_xp (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    cantidad integer NOT NULL,
    origen_tipo character varying(20) NOT NULL,
    origen_id uuid NOT NULL,
    fecha timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_texto_movimiento_xp CHECK (((origen_tipo)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.movimiento_xp OWNER TO postgres;

--
-- TOC entry 246 (class 1259 OID 37954)
-- Name: nivel_xp; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.nivel_xp (
    nivel integer NOT NULL,
    nombre character varying(50) NOT NULL,
    xp_minimo integer NOT NULL,
    CONSTRAINT ck_nexum_nivel_xp CHECK (((nivel >= 1) AND (xp_minimo >= 0))),
    CONSTRAINT ck_texto_nivel_xp CHECK (((nombre)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.nivel_xp OWNER TO postgres;

--
-- TOC entry 233 (class 1259 OID 37686)
-- Name: opcion_respuesta; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.opcion_respuesta (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    pregunta_id uuid NOT NULL,
    texto_opcion text NOT NULL,
    es_correcta boolean NOT NULL,
    CONSTRAINT ck_texto_opcion_respuesta CHECK ((texto_opcion !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.opcion_respuesta OWNER TO postgres;

--
-- TOC entry 237 (class 1259 OID 37776)
-- Name: pago; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pago (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    suscripcion_id uuid,
    curso_id uuid,
    monto numeric(10,2) NOT NULL,
    moneda character varying(3) DEFAULT 'PEN'::character varying NOT NULL,
    estado character varying(20) NOT NULL,
    metodo character varying(30) NOT NULL,
    concepto character varying(80),
    referencia character varying(120),
    fecha_pago timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_nexum_pago CHECK (((monto > (0)::numeric) AND ((moneda)::text ~ '^[A-Z]{3}$'::text) AND (upper((estado)::text) = ANY (ARRAY['PENDIENTE'::text, 'COMPLETADO'::text, 'APROBADO'::text, 'PAGADO'::text, 'FALLIDO'::text, 'CANCELADO'::text, 'REEMBOLSADO'::text])) AND ((curso_id IS NULL) <> (suscripcion_id IS NULL)))),
    CONSTRAINT ck_texto_pago CHECK ((((moneda)::text !~ '^[[:space:]]*$'::text) AND ((estado)::text !~ '^[[:space:]]*$'::text) AND ((metodo)::text !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.pago OWNER TO postgres;

--
-- TOC entry 249 (class 1259 OID 37995)
-- Name: plan_ia; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.plan_ia (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(50) NOT NULL,
    precio numeric(10,2) NOT NULL,
    moneda character varying(3) DEFAULT 'PEN'::character varying NOT NULL,
    creditos_por_periodo integer NOT NULL,
    periodo character varying(20) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_nexum_plan_ia CHECK (((precio >= (0)::numeric) AND (creditos_por_periodo >= 0) AND (lower((periodo)::text) = ANY (ARRAY['mensual'::text, 'anual'::text])) AND ((moneda)::text ~ '^[A-Z]{3}$'::text))),
    CONSTRAINT ck_texto_plan_ia CHECK ((((nombre)::text !~ '^[[:space:]]*$'::text) AND ((moneda)::text !~ '^[[:space:]]*$'::text) AND ((periodo)::text !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.plan_ia OWNER TO postgres;

--
-- TOC entry 232 (class 1259 OID 37673)
-- Name: pregunta; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.pregunta (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    examen_id uuid NOT NULL,
    texto_pregunta text NOT NULL,
    explicacion_respuesta text,
    CONSTRAINT ck_texto_pregunta CHECK ((texto_pregunta !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.pregunta OWNER TO postgres;

--
-- TOC entry 239 (class 1259 OID 37822)
-- Name: progreso_leccion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.progreso_leccion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    leccion_id uuid NOT NULL,
    completado boolean DEFAULT false NOT NULL,
    fecha_completado timestamp without time zone,
    ultimo_segundo_visto integer,
    tiempo_activo integer DEFAULT 0,
    cantidad_visitas integer DEFAULT 0,
    intentos_practica integer DEFAULT 0,
    xp_otorgada_aplicada boolean DEFAULT false NOT NULL,
    primera_actividad timestamp without time zone,
    ultima_actividad timestamp without time zone,
    CONSTRAINT ck_nexum_progreso_leccion CHECK ((((ultimo_segundo_visto IS NULL) OR (ultimo_segundo_visto >= 0)) AND ((tiempo_activo IS NULL) OR (tiempo_activo >= 0)) AND ((cantidad_visitas IS NULL) OR (cantidad_visitas >= 0)) AND ((intentos_practica IS NULL) OR (intentos_practica >= 0)) AND ((ultima_actividad IS NULL) OR (primera_actividad IS NULL) OR (ultima_actividad >= primera_actividad)) AND ((NOT completado) OR (fecha_completado IS NOT NULL))))
);


ALTER TABLE public.progreso_leccion OWNER TO postgres;

--
-- TOC entry 227 (class 1259 OID 37600)
-- Name: recurso_adjunto; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.recurso_adjunto (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    leccion_id uuid NOT NULL,
    nombre character varying(255) NOT NULL,
    tipo_archivo character varying(50),
    url_archivo text NOT NULL,
    CONSTRAINT ck_texto_recurso_adjunto CHECK ((((nombre)::text !~ '^[[:space:]]*$'::text) AND (url_archivo !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.recurso_adjunto OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 37613)
-- Name: recurso_externo_cache; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.recurso_externo_cache (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    leccion_id uuid NOT NULL,
    tipo character varying(30),
    fuente character varying(50),
    titulo text,
    descripcion text,
    thumbnail text,
    url text NOT NULL,
    metadata jsonb,
    fecha_creacion timestamp without time zone DEFAULT now() NOT NULL,
    expira_en timestamp without time zone,
    CONSTRAINT ck_nexum_recurso_externo_cache CHECK (((expira_en IS NULL) OR (expira_en > fecha_creacion))),
    CONSTRAINT ck_texto_recurso_externo_cache CHECK ((url !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.recurso_externo_cache OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 37718)
-- Name: respuesta_intento; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.respuesta_intento (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    intento_id uuid NOT NULL,
    pregunta_id uuid NOT NULL,
    opcion_id uuid,
    es_correcta boolean
);


ALTER TABLE public.respuesta_intento OWNER TO postgres;

--
-- TOC entry 240 (class 1259 OID 37846)
-- Name: seguimiento_leccion; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.seguimiento_leccion (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    leccion_id uuid NOT NULL,
    tiempo_acumulado integer DEFAULT 0 NOT NULL,
    cantidad_visitas integer DEFAULT 0 NOT NULL,
    intentos_practica integer DEFAULT 0 NOT NULL,
    primera_actividad timestamp with time zone DEFAULT now() NOT NULL,
    ultima_actividad timestamp with time zone,
    CONSTRAINT ck_nexum_seguimiento_leccion CHECK (((tiempo_acumulado >= 0) AND (cantidad_visitas >= 0) AND (intentos_practica >= 0) AND ((ultima_actividad IS NULL) OR (ultima_actividad >= primera_actividad))))
);


ALTER TABLE public.seguimiento_leccion OWNER TO postgres;

--
-- TOC entry 250 (class 1259 OID 38003)
-- Name: suscripcion_ia; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.suscripcion_ia (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    usuario_id uuid NOT NULL,
    plan_id uuid NOT NULL,
    estado character varying(20) NOT NULL,
    fecha_inicio date NOT NULL,
    fecha_fin date,
    renovacion_automatica boolean DEFAULT false NOT NULL,
    creditos_asignados integer NOT NULL,
    precio_cobrado numeric(10,2) NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_nexum_suscripcion_ia CHECK (((creditos_asignados >= 0) AND (precio_cobrado >= (0)::numeric) AND ((fecha_fin IS NULL) OR (fecha_fin > fecha_inicio)) AND (lower((estado)::text) = ANY (ARRAY['activa'::text, 'cancelada'::text, 'vencida'::text, 'pendiente'::text])))),
    CONSTRAINT ck_texto_suscripcion_ia CHECK (((estado)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.suscripcion_ia OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 37484)
-- Name: usuario; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.usuario (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    nombre character varying(100) NOT NULL,
    apellido character varying(100) NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    rol character varying(20) DEFAULT 'USUARIO'::character varying NOT NULL,
    avatar_url character varying(255),
    email_verificado boolean DEFAULT false NOT NULL,
    fecha_registro timestamp without time zone DEFAULT now() NOT NULL,
    ultima_conexion timestamp without time zone,
    updated_at timestamp without time zone,
    deleted_at timestamp without time zone,
    genero character varying(20),
    fecha_nacimiento date,
    CONSTRAINT chk_rol CHECK (((rol)::text = ANY ((ARRAY['USUARIO'::character varying, 'ADMINISTRADOR'::character varying])::text[]))),
    CONSTRAINT chk_usuario_fecha_nacimiento CHECK ((fecha_nacimiento <= CURRENT_DATE)),
    CONSTRAINT chk_usuario_genero CHECK (((genero)::text = ANY ((ARRAY['FEMENINO'::character varying, 'MASCULINO'::character varying])::text[]))),
    CONSTRAINT chk_usuario_password_bcrypt CHECK (((password_hash)::text ~ '^\$2[aby]\$(0[4-9]|[12][0-9]|3[01])\$[./A-Za-z0-9]{53}$'::text)),
    CONSTRAINT ck_nexum_usuario CHECK ((((nombre)::text !~ '^[[:space:]]*$'::text) AND ((apellido)::text !~ '^[[:space:]]*$'::text) AND ((email)::text ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'::text))),
    CONSTRAINT ck_texto_usuario CHECK ((((nombre)::text !~ '^[[:space:]]*$'::text) AND ((apellido)::text !~ '^[[:space:]]*$'::text) AND ((email)::text !~ '^[[:space:]]*$'::text) AND ((password_hash)::text !~ '^[[:space:]]*$'::text) AND ((rol)::text !~ '^[[:space:]]*$'::text)))
);


ALTER TABLE public.usuario OWNER TO postgres;

--
-- TOC entry 245 (class 1259 OID 37938)
-- Name: usuario_medalla; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.usuario_medalla (
    usuario_id uuid NOT NULL,
    medalla_id uuid NOT NULL,
    fecha_obtencion timestamp without time zone DEFAULT now() NOT NULL
);


ALTER TABLE public.usuario_medalla OWNER TO postgres;

--
-- TOC entry 247 (class 1259 OID 37961)
-- Name: usuario_xp; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.usuario_xp (
    usuario_id uuid NOT NULL,
    xp_total integer DEFAULT 0 NOT NULL,
    nivel integer DEFAULT 1 NOT NULL,
    racha_dias integer DEFAULT 0 NOT NULL,
    ultima_actividad date,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_nexum_usuario_xp CHECK (((xp_total >= 0) AND (racha_dias >= 0)))
);


ALTER TABLE public.usuario_xp OWNER TO postgres;

--
-- TOC entry 243 (class 1259 OID 37908)
-- Name: valoracion_curso; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.valoracion_curso (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    curso_id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    puntaje smallint NOT NULL,
    comentario text,
    fecha timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT chk_puntaje CHECK (((puntaje >= 1) AND (puntaje <= 5)))
);


ALTER TABLE public.valoracion_curso OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 37585)
-- Name: video_streaming; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.video_streaming (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    leccion_id uuid NOT NULL,
    aws_s3_key character varying(255) NOT NULL,
    aws_cloudfront_url character varying(255),
    calidad_disponible character varying(50),
    duracion_segundos integer,
    CONSTRAINT ck_nexum_video_streaming CHECK (((duracion_segundos IS NULL) OR (duracion_segundos >= 0))),
    CONSTRAINT ck_texto_video_streaming CHECK (((aws_s3_key)::text !~ '^[[:space:]]*$'::text))
);


ALTER TABLE public.video_streaming OWNER TO postgres;

--
-- TOC entry 5446 (class 0 OID 37627)
-- Dependencies: 229
-- Data for Name: actividad; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.actividad (id, leccion_id, titulo, tipo, instrucciones, xp_otorgada) FROM stdin;
\.


--
-- TOC entry 5453 (class 0 OID 37740)
-- Dependencies: 236
-- Data for Name: calificacion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.calificacion (id, usuario_id, curso_id, leccion_id, intento_examen_id, entrega_actividad_id, tipo, titulo, nota, nota_maxima, peso, comentario, fecha_registro) FROM stdin;
\.


--
-- TOC entry 5438 (class 0 OID 37513)
-- Dependencies: 221
-- Data for Name: categoria; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.categoria (id, nombre, icono_url) FROM stdin;
d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a44	Redes y Ciberseguridad	\N
d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a55	Programación	\N
d3eebc99-9c0b-4ef8-bb6d-6bb9bd380a66	Cloud Computing	\N
d4eebc99-9c0b-4ef8-bb6d-6bb9bd380a77	Inteligencia Artificial	\N
\.


--
-- TOC entry 5458 (class 0 OID 37865)
-- Dependencies: 241
-- Data for Name: certificado; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.certificado (id, inscripcion_id, codigo_verificacion, url_pdf, fecha_emision, nombre_alumno, titulo_curso) FROM stdin;
\.


--
-- TOC entry 5437 (class 0 OID 37497)
-- Dependencies: 220
-- Data for Name: codigo_verificacion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.codigo_verificacion (id, usuario_id, tipo, codigo_hash, usado, expira_en, fecha_creacion) FROM stdin;
\.


--
-- TOC entry 5459 (class 0 OID 37883)
-- Dependencies: 242
-- Data for Name: comentario_leccion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.comentario_leccion (id, leccion_id, usuario_id, comentario_padre_id, contenido, fecha, deleted_at) FROM stdin;
\.


--
-- TOC entry 5468 (class 0 OID 38028)
-- Dependencies: 251
-- Data for Name: consumo_ia; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.consumo_ia (id, suscripcion_id, usuario_id, mensaje_id, creditos_usados, fecha) FROM stdin;
\.


--
-- TOC entry 5439 (class 0 OID 37519)
-- Dependencies: 222
-- Data for Name: curso; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.curso (id, responsable_id, titulo, slug, descripcion, recomendado_para, estado, nivel, duracion_estimada, ruta, categoria_academica, precio, moneda, xp_otorgada, promedio_valoracion, fecha_creacion, updated_at, deleted_at, portada_url) FROM stdin;
e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11	Fundamentos de Redes	fundamentos-redes	Curso introductorio a redes.	\N	PUBLICADO	principiante	25 horas	\N	\N	0.00	PEN	500	\N	2026-10-23 00:00:42.577721	\N	\N	\N
6d83738d-a069-49ca-8872-565afe5ad391	a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11	Web Integrado	web-integrado	web	Estudiantes con conocimientos básicos de HTML, CSS, JavaScript y Java.	PUBLICADO	PRINCIPIANTE	2 meses	Desarrollo Web	Tecnología y programación	20.00	PEN	\N	\N	2026-10-23 09:11:20.956601	2026-10-23 09:12:52.901769	\N	\N
b1b6d0ff-4d6b-4972-9c92-794546fe0ba8	a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11	javaScript	javascript	node.js	basico	PUBLICADO	INTERMEDIO	2 meses	java	Tecnología y programación	0.00	PEN	\N	\N	2026-10-23 09:16:53.105362	2026-10-23 09:17:00.150221	\N	\N
\.


--
-- TOC entry 5440 (class 0 OID 37540)
-- Dependencies: 223
-- Data for Name: curso_categoria; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.curso_categoria (curso_id, categoria_id) FROM stdin;
e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a44
6d83738d-a069-49ca-8872-565afe5ad391	d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a55
b1b6d0ff-4d6b-4972-9c92-794546fe0ba8	d2eebc99-9c0b-4ef8-bb6d-6bb9bd380a55
\.


--
-- TOC entry 5447 (class 0 OID 37640)
-- Dependencies: 230
-- Data for Name: entrega_actividad; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.entrega_actividad (id, actividad_id, usuario_id, contenido, url_archivo, estado, fecha_entrega) FROM stdin;
\.


--
-- TOC entry 5448 (class 0 OID 37660)
-- Dependencies: 231
-- Data for Name: examen; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.examen (id, leccion_id, titulo, intentos_maximos, puntaje_minimo_aprobacion, xp_otorgada, publicado) FROM stdin;
8828bc78-50e4-5835-b6c9-9290ffd7a97f	c0de2026-1023-4000-8000-000000000002	Evaluacion de la leccion	3	70.00	\N	t
2349a3e1-429c-5091-8eaf-a38290a9b3c5	c0de2026-1023-4000-8000-000000000006	Evaluacion de la leccion	3	70.00	\N	t
ba2a9167-bae0-5a9c-bc1f-648a9029810c	c0de2026-1023-4000-8000-000000000009	Evaluacion de la leccion	3	70.00	\N	t
\.


--
-- TOC entry 5472 (class 0 OID 38100)
-- Dependencies: 255
-- Data for Name: ia_analisis_leccion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ia_analisis_leccion (id, usuario_id, leccion_id, nivel, duracion, resumen, consejo, siguiente_paso, objetivos, proyecto, fecha_creacion) FROM stdin;
\.


--
-- TOC entry 5469 (class 0 OID 38046)
-- Dependencies: 252
-- Data for Name: ia_conversacion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ia_conversacion (id, usuario_id, titulo, fecha_creacion) FROM stdin;
\.


--
-- TOC entry 5471 (class 0 OID 38079)
-- Dependencies: 254
-- Data for Name: ia_conversacion_contexto; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ia_conversacion_contexto (id, conversacion_id, curso_id, leccion_id) FROM stdin;
\.


--
-- TOC entry 5470 (class 0 OID 38059)
-- Dependencies: 253
-- Data for Name: ia_mensaje; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.ia_mensaje (id, conversacion_id, rol, mensaje, acciones, fecha_creacion) FROM stdin;
\.


--
-- TOC entry 5455 (class 0 OID 37795)
-- Dependencies: 238
-- Data for Name: inscripcion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.inscripcion (id, usuario_id, curso_id, pago_id, estado, progreso_porcentaje, fecha_inscripcion) FROM stdin;
eb52675d-cbea-4529-b41a-7fa0978ef73f	b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22	e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	\N	ACTIVA	8.33	2026-10-23 00:02:02.144542
ff5f411f-ecc1-479f-a5ce-2db4eff54d43	ad4b9272-4ef6-418e-935d-3e68475f95b9	e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	\N	ACTIVA	0.00	2026-10-23 08:20:51.146263
c59aa2bf-8a38-4c62-b681-4136493994a8	b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22	b1b6d0ff-4d6b-4972-9c92-794546fe0ba8	\N	ACTIVA	0.00	2026-10-23 09:17:11.258387
\.


--
-- TOC entry 5451 (class 0 OID 37699)
-- Dependencies: 234
-- Data for Name: intento_examen; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.intento_examen (id, examen_id, usuario_id, numero_intento, puntaje, aprobado, iniciado_en, finalizado_en) FROM stdin;
\.


--
-- TOC entry 5442 (class 0 OID 37570)
-- Dependencies: 225
-- Data for Name: leccion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.leccion (id, modulo_id, titulo, slug, orden, tipo, dificultad, duracion_estimada, duracion_minima, contenido_texto, puntos_otorgados, xp_otorgada, video_url, imagen_url, reto_practico) FROM stdin;
aaeebc99-9c0b-4ef8-bb6d-6bb9bd380b01	f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01	¿Qué es una red de computadoras?	que-es-una-red	1	video	\N	\N	\N	Una red de computadoras es un conjunto de dispositivos interconectados.	\N	20	\N	\N	\N
aaeebc99-9c0b-4ef8-bb6d-6bb9bd380b02	f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01	Tipos de redes: LAN, WAN, MAN	tipos-de-redes	2	lectura	\N	\N	\N	Existen diferentes tipos de redes según su alcance geográfico.	\N	25	\N	\N	\N
aaeebc99-9c0b-4ef8-bb6d-6bb9bd380b03	f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01	Componentes básicos de una red	componentes-basicos	3	video	\N	\N	\N	Hosts, dispositivos intermediarios, medios de red y servicios.	\N	30	\N	\N	\N
c0de2026-1023-4000-8000-000000000001	f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01	Modelos OSI y TCP/IP: cómo viajan los datos	modelos-osi-tcpip	4	lectura	principiante	20 minutos	\N	OBJETIVO\nReconocer por qué una comunicación de red se organiza en capas.\n\nMODELO OSI\nSus siete capas son física, enlace, red, transporte, sesión, presentación y aplicación. Cada capa ofrece servicios a la siguiente. La capa física transmite señales; enlace identifica equipos dentro de un segmento mediante direcciones MAC; red permite comunicar redes con direcciones IP.\n\nMODELO TCP/IP\nSuele describirse con cuatro capas: acceso a la red, Internet, transporte y aplicación. No existe una correspondencia exacta uno a uno con todas las capas OSI. Los modelos ayudan a explicar las funciones; no son dispositivos físicos.\n\nEJEMPLO\nAl abrir una web, HTTP pertenece a aplicación, TCP puede transportar sus datos, IP identifica el destino y Ethernet o Wi-Fi mueve las tramas en el enlace local. HTTP/3 emplea QUIC sobre UDP: no toda web utiliza TCP.\n\nENCAPSULACIÓN\nLas capas añaden información de control. El receptor interpreta y retira esa información. Un switch Ethernet suele reenviar tramas según MAC; un router reenvía paquetes según rutas IP.\n\nCOMPROBACIÓN\nSi un cable está desconectado, empieza por física. Si hay enlace pero no puedes llegar a otra red, revisa IP, máscara, puerta de enlace y rutas. Diagnosticar por capas evita cambiar configuraciones sin una hipótesis.	\N	30	\N	\N	En una hoja dibuja el recorrido equipo → switch → router → servidor. Anota qué dispositivo consulta MAC y cuál consulta rutas IP. Explica qué revisarías primero si el equipo no detecta el cable.
c0de2026-1023-4000-8000-000000000002	f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01	Repaso: diseña una red para una pequeña oficina	repaso-red-oficina	5	lectura	principiante	25 minutos	\N	OBJETIVO\nAplicar los conceptos del primer módulo a una situación real.\n\nESCENARIO\nUna oficina tiene seis computadoras, una impresora de red y dos celulares. Todos requieren acceso a Internet y las computadoras necesitan compartir la impresora.\n\nPROPUESTA\nUn switch permite conectar los equipos cableados. Un punto de acceso ofrece Wi-Fi a los celulares. El router conecta la red local con otras redes. En equipos domésticos estas funciones pueden estar integradas en una sola caja, pero siguen siendo funciones diferentes.\n\nALCANCE\nLa oficina constituye una LAN. La conexión del proveedor hacia otras ubicaciones forma parte de una red de mayor alcance. Una LAN puede funcionar sin Internet: compartir archivos localmente no exige necesariamente salir a Internet.\n\nTOPOLOGÍA\nUna estrella conecta los equipos a un punto central. Si falla el enlace de una computadora, las demás pueden seguir conectadas; si falla el switch central, se afecta a todos los equipos que dependen de él.\n\nSEGURIDAD BÁSICA\nCambia las credenciales predeterminadas, actualiza los equipos y separa invitados cuando sea posible. Conocer el nombre de una red Wi-Fi no demuestra que sea confiable.\n\nCIERRE\nResuelve el cuestionario de repaso y revisa las explicaciones antes de continuar a direccionamiento IP.	\N	35	\N	\N	Dibuja la oficina con sus ocho dispositivos y la impresora. Identifica enlaces cableados e inalámbricos, switch, punto de acceso y router. Explica qué servicio seguiría funcionando si se interrumpe Internet.
c0de2026-1023-4000-8000-000000000003	f2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02	Direcciones IPv4 y máscaras de red	ipv4-mascaras	1	lectura	principiante	20 minutos	\N	OBJETIVO\nInterpretar una dirección IPv4 y su prefijo.\n\nDIRECCIÓN IPv4\nTiene 32 bits y suele escribirse en cuatro octetos decimales, cada uno entre 0 y 255. Una dirección como 192.168.10.25 identifica una interfaz dentro del contexto de su red; el mismo valor puede existir en redes privadas aisladas.\n\nMÁSCARA Y PREFIJO\nEl prefijo /24 indica que los primeros 24 bits identifican la red. Su máscara es 255.255.255.0. Para 192.168.10.25/24, la red es 192.168.10.0 y el broadcast es 192.168.10.255.\n\nEQUIPOS EN LA MISMA SUBRED\n192.168.10.25/24 y 192.168.10.40/24 pertenecen a la misma subred. 192.168.11.40/24 pertenece a otra. Comparar solo los primeros tres números no funciona con todos los prefijos: hay que usar la máscara.\n\nDIRECCIONES PRIVADAS\nLos bloques privados IPv4 son 10.0.0.0/8, 172.16.0.0/12 y 192.168.0.0/16. No se enrutan globalmente en Internet como direcciones públicas.\n\nERRORES FRECUENTES\nNo uses 256 como octeto. Evita asignar la misma dirección a dos interfaces del mismo segmento. Comprueba la máscara junto con la IP: una máscara incorrecta cambia lo que el equipo considera local.	\N	35	\N	\N	Para 192.168.20.34/24 identifica red, broadcast y una dirección válida para otro equipo. Decide si 192.168.21.10/24 pertenece a la misma subred y justifica tu respuesta.
c0de2026-1023-4000-8000-000000000004	f2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02	Subredes: de /24 a /26 paso a paso	subredes-24-26	2	lectura	principiante	25 minutos	\N	OBJETIVO\nDividir una red /24 en cuatro subredes /26.\n\nPUNTO DE PARTIDA\n192.168.10.0/24 contiene 256 direcciones. Al pasar a /26 quedan 6 bits para direcciones dentro de cada subred: 2 elevado a 6 = 64 direcciones por bloque. La máscara /26 es 255.255.255.192.\n\nLOS CUATRO BLOQUES\n192.168.10.0/26: hosts .1 a .62; broadcast .63.\n192.168.10.64/26: hosts .65 a .126; broadcast .127.\n192.168.10.128/26: hosts .129 a .190; broadcast .191.\n192.168.10.192/26: hosts .193 a .254; broadcast .255.\n\nCAPACIDAD\nCada una de estas subredes admite convencionalmente 62 direcciones de host, porque una identifica la red y otra el broadcast. La regla de restar dos no se aplica sin matices a todos los prefijos: /31 y /32 tienen usos especiales.\n\nUBICAR UN EQUIPO\n192.168.10.70/26 pertenece al bloque que comienza en .64. Su broadcast es .127. La dirección .64 no se asigna como dirección convencional de host en este bloque.\n\nPLANIFICACIÓN\nNo basta con que dos equipos tengan direcciones 192.168.10.x para pertenecer a la misma subred. Por ejemplo .10/26 y .70/26 están en bloques diferentes y requieren enrutamiento para comunicarse.	\N	40	\N	\N	Ubica 192.168.10.150/26: red, primer host, último host y broadcast. Luego calcula cuántas subredes /26 obtienes de un /24. Respuesta de referencia: red .128, hosts .129 a .190, broadcast .191 y cuatro subredes.
c0de2026-1023-4000-8000-000000000005	f2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02	Puerta de enlace, DHCP y DNS	gateway-dhcp-dns	3	lectura	principiante	20 minutos	\N	OBJETIVO\nDistinguir tres servicios que suelen confundirse.\n\nPUERTA DE ENLACE\nEs el siguiente salto que usa el equipo para destinos que no resuelve mediante una ruta más específica. En una LAN habitual es la interfaz del router situada en la misma subred. No tiene que terminar en .1: debe ser una dirección válida de esa red y estar configurada correctamente.\n\nDHCP\nPermite obtener configuración de red automáticamente, por ejemplo IP, máscara, router y servidores DNS. El servidor ofrece una concesión durante un período. Un equipo con IP manual puede funcionar sin DHCP si su configuración es correcta y no provoca conflictos.\n\nDNS\nResuelve nombres y otros registros. Para acceder a un servicio por nombre es necesario resolverlo, salvo que exista otra fuente válida como una caché. Tener DNS operativo no sustituye la conectividad hacia el destino.\n\nDIAGNÓSTICO\nSi puedes comunicarte con una IP remota pero no resolver un nombre, investiga DNS. Si solo falla el acceso fuera de la subred, revisa la puerta de enlace y las rutas. Un ping sin respuesta no demuestra por sí solo que un destino esté apagado: puede haber filtrado.\n\nEJEMPLO\nEquipo 192.168.20.10/24, router 192.168.20.1 y DNS proporcionado por la organización. Los tres valores tienen funciones distintas.	\N	35	\N	\N	Completa una ficha con IP, prefijo, puerta de enlace y DNS de una red ficticia. Describe dos síntomas diferentes: uno compatible con falla de DNS y otro compatible con una puerta de enlace incorrecta.
c0de2026-1023-4000-8000-000000000006	f2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02	Laboratorio: configura y diagnostica dos equipos	laboratorio-ip	4	lectura	principiante	30 minutos	\N	OBJETIVO\nPracticar una configuración coherente y documentar el diagnóstico.\n\nENTORNO\nUsa un simulador de redes o dos equipos de laboratorio. No cambies la configuración de una red de trabajo sin autorización. Este ejercicio no requiere conectarse a Internet.\n\nCONFIGURACIÓN\nRed 192.168.50.0/24. Equipo A: 192.168.50.10/24. Equipo B: 192.168.50.11/24. Ambos conectados al mismo switch. Si hay un router, puedes usar 192.168.50.1 como su interfaz de la LAN.\n\nPASOS\n1. Dibuja la topología y revisa los enlaces.\n2. Asigna las direcciones sin duplicados.\n3. Comprueba IP y máscara en ambos equipos.\n4. Intenta comunicar A con B por su dirección IP.\n5. Revisa el resultado antes de modificar más valores.\n\nFALLA CONTROLADA\nEn el simulador, cambia B a 192.168.51.11/24. Ahora está en otra subred. Sin enrutamiento adecuado no esperes la misma comunicación local. Restaura la dirección original y compara.\n\nEVIDENCIAS\nGuarda el diagrama, la tabla de direccionamiento y una explicación de la falla. Distingue conectividad local, acceso a otras redes y resolución de nombres.\n\nREPASO\nResuelve las preguntas para comprobar que puedes separar máscara, dirección de red, DHCP y DNS.	\N	45	\N	\N	Entrega una tabla de A y B con IP, máscara y puerta de enlace si existe. Incluye la falla que introdujiste, el síntoma observado y cómo la corregiste. Si no tienes simulador, resuelve el caso por escrito.
c0de2026-1023-4000-8000-000000000007	f3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03	Cómo aprende un switch: MAC y tramas Ethernet	switch-mac-tramas	1	lectura	principiante	20 minutos	\N	OBJETIVO\nExplicar el aprendizaje de direcciones MAC y el reenvío de tramas.\n\nAPRENDIZAJE\nUn switch Ethernet aprende la dirección MAC de origen de una trama y la asocia con el puerto de entrada y la VLAN. La tabla tiene tiempos de envejecimiento; no es un registro permanente de todos los equipos.\n\nDESTINO CONOCIDO\nSi conoce el puerto del destino dentro de esa VLAN, reenvía la trama por ese puerto cuando corresponde. Si el destino está en el mismo puerto por el que llegó, normalmente filtra la trama.\n\nDESTINO DESCONOCIDO\nUna trama unicast cuyo destino no se conoce se difunde por los puertos elegibles de la misma VLAN, excepto el puerto de entrada. Eso no significa enviarla por todas las redes de la organización.\n\nBROADCAST\nLa trama de broadcast también se distribuye dentro de su dominio de broadcast. Los routers no reenvían normalmente broadcasts Ethernet entre subredes.\n\nMAC E IP\nMAC se utiliza en el enlace local; IP permite direccionamiento y enrutamiento entre redes. Una dirección MAC no reemplaza una dirección IP.\n\nBUCLES\nConectar enlaces redundantes sin mecanismos adecuados puede crear bucles de capa 2. STP ayuda a construir una topología sin bucles bloqueando caminos redundantes según su funcionamiento.	\N	35	\N	\N	Dibuja un switch con A en el puerto 1 y B en el puerto 2. Parte de una tabla vacía y describe qué aprende al recibir la primera trama de A y qué cambia después de una respuesta de B.
c0de2026-1023-4000-8000-000000000008	f3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03	VLAN: separa grupos dentro de una red	vlan-acceso-trunk	2	lectura	principiante	25 minutos	\N	OBJETIVO\nDiferenciar puertos de acceso, enlaces troncales y comunicación entre VLAN.\n\nQUÉ ES UNA VLAN\nUna VLAN separa lógicamente un dominio de broadcast dentro de una infraestructura de switching. Dos equipos en VLAN diferentes necesitan enrutamiento para comunicarse, aunque estén conectados al mismo switch físico.\n\nPUERTO DE ACCESO\nEn un caso habitual transporta el tráfico de datos de una VLAN para un dispositivo final. Hay configuraciones especiales, por ejemplo para voz, que requieren más detalles.\n\nENLACE TRONCAL\nUn trunk puede transportar tráfico de varias VLAN. Con IEEE 802.1Q, las etiquetas permiten distinguirlas; el tratamiento de la VLAN nativa depende de la configuración. Los extremos deben tener parámetros coherentes.\n\nEJEMPLO\nAdministración usa VLAN 10 y estudiantes VLAN 20. Cada grupo utiliza una subred diferente. Si ambos necesitan comunicarse, se configura un router o switch con capacidad de capa 3 y las políticas correspondientes.\n\nSEGURIDAD\nSeparar VLAN limita dominios de broadcast, pero no sustituye controles de acceso, autenticación ni filtrado. No asumas que crear una VLAN garantiza por sí solo aislamiento completo de seguridad.\n\nVERIFICACIÓN\nRevisa la VLAN del puerto, las VLAN permitidas en el trunk y las rutas entre subredes. Documenta la función de cada enlace.	\N	40	\N	\N	Diseña VLAN 10 con 192.168.10.0/24 y VLAN 20 con 192.168.20.0/24. Marca los puertos de acceso y un trunk entre switches. Explica qué equipo permitiría comunicación entre los dos grupos.
c0de2026-1023-4000-8000-000000000009	f3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03	Proyecto final: una red de oficina segmentada	proyecto-final-redes	3	lectura	principiante	35 minutos	\N	OBJETIVO\nIntegrar topología, direccionamiento y switching en una propuesta verificable.\n\nESCENARIO\nUna oficina tiene dos grupos: administración y formación. Cada grupo necesita equipos cableados. Se requiere acceso a recursos compartidos y una separación lógica entre grupos.\n\nDISEÑO PROPUESTO\nUsa un switch administrable con VLAN 10 y VLAN 20. Asigna 192.168.10.0/24 y 192.168.20.0/24 respectivamente. Un router o un equipo de capa 3 puede conectar ambas subredes cuando la política lo permita.\n\nTABLA DE DIRECCIONAMIENTO\nAdministración: equipo A 192.168.10.10/24, puerta de enlace 192.168.10.1.\nFormación: equipo B 192.168.20.10/24, puerta de enlace 192.168.20.1.\nEstas puertas de enlace deben existir en interfaces correctamente configuradas: escribirlas en los equipos no crea el servicio de enrutamiento.\n\nPLAN DE VALIDACIÓN\nComprueba enlaces, VLAN de cada puerto, IP y máscara. Verifica la comunicación dentro de cada VLAN. Después comprueba el enrutamiento y las reglas necesarias entre VLAN. Añade DNS solo si necesitas resolver nombres.\n\nENTREGABLES\nDiagrama, tabla de direcciones, asignación de puertos y tres comprobaciones con resultado esperado. Incluye un fallo de configuración y cómo lo diagnosticarías.\n\nAUTOEVALUACIÓN\nExplica la diferencia entre switch y router, entre MAC e IP y entre un puerto de acceso y un trunk. Completa el cuestionario final de repaso.	\N	50	\N	\N	Prepara un informe de una página con tu topología y tabla IP. Incluye una prueba dentro de la misma VLAN, otra entre VLAN y una comprobación de DNS. Explica cuándo una prueba puede fallar aunque el enlace físico esté activo.
4ae9ad13-5dff-4fa4-b400-542b26e678f7	eb20084f-85a2-437f-8afe-8029077d15bc	crear	crear	1	TEXTO	2	2	\N	eedds	\N	8	\N	\N	\N
46cb205b-56d0-499f-be4e-336af9212bcc	eb20084f-85a2-437f-8afe-8029077d15bc	crear t	crear-t	2	VIDEO	2	2	\N	sssaasasa	\N	8	\N	\N	\N
\.


--
-- TOC entry 5461 (class 0 OID 37930)
-- Dependencies: 244
-- Data for Name: medalla; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.medalla (id, nombre, descripcion, imagen_url, criterio_tipo, criterio_valor) FROM stdin;
cceebc99-9c0b-4ef8-bb6d-6bb9bd380d01	Primera Lección	Completaste tu primera lección	\N	lecciones_completadas	1
cceebc99-9c0b-4ef8-bb6d-6bb9bd380d02	Racha de 3 días	Estudiaste 3 días seguidos	\N	racha_dias	3
cceebc99-9c0b-4ef8-bb6d-6bb9bd380d03	Primer Curso	Completaste tu primer curso	\N	cursos_completados	1
\.


--
-- TOC entry 5441 (class 0 OID 37555)
-- Dependencies: 224
-- Data for Name: modulo; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.modulo (id, curso_id, titulo, slug, descripcion, orden, xp_otorgada) FROM stdin;
f1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01	e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	Introducción a las Redes	introduccion-redes	\N	1	80
f2eebc99-9c0b-4ef8-bb6d-6bb9bd380a02	e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	Direccionamiento IP	direccionamiento-ip	\N	2	100
f3eebc99-9c0b-4ef8-bb6d-6bb9bd380a03	e1eebc99-9c0b-4ef8-bb6d-6bb9bd380a88	Switching Básico	switching-basico	\N	3	120
eb20084f-85a2-437f-8afe-8029077d15bc	b1b6d0ff-4d6b-4972-9c92-794546fe0ba8	angular	angular	angular y ya	1	\N
\.


--
-- TOC entry 5465 (class 0 OID 37980)
-- Dependencies: 248
-- Data for Name: movimiento_xp; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.movimiento_xp (id, usuario_id, cantidad, origen_tipo, origen_id, fecha) FROM stdin;
\.


--
-- TOC entry 5463 (class 0 OID 37954)
-- Dependencies: 246
-- Data for Name: nivel_xp; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.nivel_xp (nivel, nombre, xp_minimo) FROM stdin;
1	Principiante	0
2	Aprendiz	500
3	Intermedio	1500
4	Avanzado	3500
5	Experto	7000
6	Maestro	12000
\.


--
-- TOC entry 5450 (class 0 OID 37686)
-- Dependencies: 233
-- Data for Name: opcion_respuesta; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.opcion_respuesta (id, pregunta_id, texto_opcion, es_correcta) FROM stdin;
f9ee2e50-1632-5182-9322-d95c3b5097e7	49f20167-5b5c-5242-a569-41155f546cc1	Un router	t
0b20d11e-6db3-5b45-875a-c23126fea626	49f20167-5b5c-5242-a569-41155f546cc1	Un cable Ethernet	f
834dbcf0-1931-5e92-b082-fe0f6839256c	49f20167-5b5c-5242-a569-41155f546cc1	Una impresora	f
f2f4131d-0440-5590-9635-7df8e810b225	49f20167-5b5c-5242-a569-41155f546cc1	Un repetidor únicamente	f
d5b2eaf6-4c76-5dae-8376-378a78e991d9	e580f68b-dc71-5c6c-984b-64ac75d74888	Nunca	f
f20e695a-a979-5cbe-940b-b30da044dbbd	e580f68b-dc71-5c6c-984b-64ac75d74888	Sí, si la red local y el servicio siguen disponibles	t
43269e1c-50f2-5b96-9cdf-52095b12df73	e580f68b-dc71-5c6c-984b-64ac75d74888	Solo cambiando todas las IP	f
1ca00c8b-e02c-5d4d-acb6-2c5b170244b1	e580f68b-dc71-5c6c-984b-64ac75d74888	Solo con DNS público	f
d82141de-ec15-56a4-ac70-ee7f1bf28ee5	1c239dd2-06a9-539c-a762-c8af19afc738	Resuelve nombres	f
662be97c-f5e9-5dfc-b39b-a0ebaba1595d	1c239dd2-06a9-539c-a762-c8af19afc738	Siempre asigna IP	f
059d87e2-75cb-5311-aec6-8d76c96ad2f6	1c239dd2-06a9-539c-a762-c8af19afc738	Reenvía tramas según MAC	t
96e67422-37a7-57aa-8ea5-df4132df01f6	1c239dd2-06a9-539c-a762-c8af19afc738	Emite certificados	f
d8d08e29-9e5f-54d6-b251-c230af9917e1	a2d6a192-66d7-54c9-b3ac-210662915daf	DNS	f
4a056edb-8e34-5b07-b72f-60cd17ca05f5	a2d6a192-66d7-54c9-b3ac-210662915daf	Correo	f
9ef7b341-cca6-5829-8b92-82140bbc3e45	a2d6a192-66d7-54c9-b3ac-210662915daf	Navegador	f
d0c0d4d7-697d-578c-9205-851166194e29	a2d6a192-66d7-54c9-b3ac-210662915daf	Cable y puerto físico	t
3b2feeb8-6e3b-5815-b70a-84614109e2b7	b34913b2-f0d7-55df-b098-a58a4dec4e7c	Aplicación, red, física	f
843db8f2-268c-57d3-a167-151c23a728df	b34913b2-f0d7-55df-b098-a58a4dec4e7c	Física, enlace, red, transporte, sesión, presentación, aplicación	t
0aef9a12-dbac-50a3-8f51-0b5278015573	b34913b2-f0d7-55df-b098-a58a4dec4e7c	Física, transporte, enlace, aplicación	f
bbac6a47-1b40-55b1-a11f-9cee3937c1f9	b34913b2-f0d7-55df-b098-a58a4dec4e7c	Red, aplicación, enlace, física	f
7c4b3cd8-2e0b-5af8-879f-ea4ac0a08d56	20527a0b-e7a3-590f-99a1-0a38dc70272a	192.168.50.0	t
d74aef41-9a1b-5f94-acf9-c5bbf5920f67	20527a0b-e7a3-590f-99a1-0a38dc70272a	192.168.50.10	f
9fa38895-6091-5015-9ef5-6496ebad0e11	20527a0b-e7a3-590f-99a1-0a38dc70272a	192.168.0.0	f
41c50920-0760-50c6-8d78-0c1167894100	20527a0b-e7a3-590f-99a1-0a38dc70272a	192.168.50.255	f
36983824-3abd-54f5-8bbe-2bca088337d1	c0cb2824-b2a1-5f83-9636-d26dfdcf536d	255.255.0.0	f
107668d8-00cc-5211-882b-090c37fc4738	c0cb2824-b2a1-5f83-9636-d26dfdcf536d	255.255.255.0	f
592db9fb-9dd8-5529-a014-5b51922841f4	c0cb2824-b2a1-5f83-9636-d26dfdcf536d	255.255.255.192	t
b3baa378-cd89-5542-a8b2-5d62fbcf8310	c0cb2824-b2a1-5f83-9636-d26dfdcf536d	255.255.255.252	f
2ca415f6-a4e7-5b7f-9f7c-6c832aaf3651	c5db8cb0-b49e-50c6-b776-b745e120153d	192.168.10.0/26	f
93fb988f-4d5e-5a6f-a7bc-43680a870168	c5db8cb0-b49e-50c6-b776-b745e120153d	192.168.10.64/26	f
71ddf5b6-ffdb-51a8-ba0c-73f1da3f0f68	c5db8cb0-b49e-50c6-b776-b745e120153d	192.168.10.192/26	f
4dbc37a8-ce4a-53ee-b39a-76c31240e14e	c5db8cb0-b49e-50c6-b776-b745e120153d	192.168.10.128/26	t
6070fc9d-a4f2-58e8-8f6f-df0b9bedd903	79a2a9aa-40f0-5306-b2fb-d6feb2d02bee	Solo el cable	f
99a5251a-15f1-5604-8f68-d9a80cd4b63f	79a2a9aa-40f0-5306-b2fb-d6feb2d02bee	DNS	t
24299415-9ef9-5390-9f00-f52a9859a16e	79a2a9aa-40f0-5306-b2fb-d6feb2d02bee	Cambiar todo el switch	f
8946b6c6-acfb-5c43-8f48-bd494e04fb8f	79a2a9aa-40f0-5306-b2fb-d6feb2d02bee	El monitor	f
49303857-a6e8-5556-b1dc-a11cfb55a4d1	02559bd2-5036-5af7-96e3-235f5f34312a	Entrega automáticamente parámetros de red	t
05152e30-aa02-523a-acf7-50910740e740	02559bd2-5036-5af7-96e3-235f5f34312a	Sustituye todas las rutas	f
a336c558-77c5-5dc6-9eb4-fe95862539d3	02559bd2-5036-5af7-96e3-235f5f34312a	Garantiza acceso a cualquier web	f
e471705f-1b54-506b-8edf-29f87669727f	02559bd2-5036-5af7-96e3-235f5f34312a	Cifra toda la LAN	f
de625584-07af-5c02-8707-2c5437f531f4	93443298-6f18-5ede-b554-ea9d776aec07	La MAC de destino como origen	f
5e8ef3b4-93b5-55a7-82fe-f019643e369d	93443298-6f18-5ede-b554-ea9d776aec07	La URL del navegador	f
a8bcaf38-17ff-5882-849c-9b31f31631a4	93443298-6f18-5ede-b554-ea9d776aec07	La MAC de origen y su puerto de entrada	t
772cf6e2-3e11-5dd3-bd47-84a2e9c042df	93443298-6f18-5ede-b554-ea9d776aec07	La IP pública de todos los equipos	f
54cc44da-322e-58e9-add8-b38c00752e66	b313bac5-d323-55c0-9e2d-2f27f2286b86	Siempre se descarta	f
c3fd3b4f-5ff7-597b-88c2-b4a348ba14bf	b313bac5-d323-55c0-9e2d-2f27f2286b86	Se difunde por puertos elegibles de la misma VLAN, excepto el de entrada	t
035316d8-e924-5541-a919-9960715e8e31	b313bac5-d323-55c0-9e2d-2f27f2286b86	Se envía a todas las VLAN	f
2bcf0f98-092b-5f42-9a93-bcffdd44a252	b313bac5-d323-55c0-9e2d-2f27f2286b86	Se convierte siempre en tráfico DNS	f
992708f8-bac4-5bd9-82ba-d655c27f6a9f	6deabcf5-8c41-59a3-927d-e850f80946bb	Un trunk	t
58ae2ff3-4add-5822-aeb7-325184d6325f	6deabcf5-8c41-59a3-927d-e850f80946bb	Un cable de alimentación	f
93aab432-416c-5f62-8516-fe8f82212f09	6deabcf5-8c41-59a3-927d-e850f80946bb	Solo un puerto USB	f
5c7ac0ed-5bcd-5d08-a372-03b692bb29c0	6deabcf5-8c41-59a3-927d-e850f80946bb	Un puerto de acceso de una única VLAN	f
b87c30db-718e-5c4b-9eee-414b8555e714	2f16b953-ded9-5a09-b959-13690033dafc	Solo tener nombres parecidos	f
6ea68346-e8ac-5fbb-b572-2d216b851e07	2f16b953-ded9-5a09-b959-13690033dafc	Compartir el mismo número de puerto	f
cddd7b9a-b299-5254-b1b6-ef7131687ee5	2f16b953-ded9-5a09-b959-13690033dafc	Desactivar la máscara	f
0d18c282-f961-5eaa-80a5-109beb5b7042	2f16b953-ded9-5a09-b959-13690033dafc	Enrutamiento y las políticas de acceso necesarias	t
c5bbbaf5-a5a7-52aa-a90b-c0037c78a53a	6a0283b9-7d8f-53bd-ac32-cde880982337	Sí, siempre	f
6d96d229-d674-5e8f-8cf6-dc0d747b32b6	6a0283b9-7d8f-53bd-ac32-cde880982337	No; también hacen falta controles de acceso y configuración adecuada	t
d867ed3b-4b8a-5ec1-901f-43490475ac2d	6a0283b9-7d8f-53bd-ac32-cde880982337	Sí, si usan IP privada	f
54c0e349-cdcb-5c15-9883-cf4046b54dab	6a0283b9-7d8f-53bd-ac32-cde880982337	Solo si hay dos switches	f
\.


--
-- TOC entry 5454 (class 0 OID 37776)
-- Dependencies: 237
-- Data for Name: pago; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.pago (id, usuario_id, suscripcion_id, curso_id, monto, moneda, estado, metodo, concepto, referencia, fecha_pago) FROM stdin;
\.


--
-- TOC entry 5466 (class 0 OID 37995)
-- Dependencies: 249
-- Data for Name: plan_ia; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.plan_ia (id, nombre, precio, moneda, creditos_por_periodo, periodo, activo) FROM stdin;
bbeebc99-9c0b-4ef8-bb6d-6bb9bd380c01	Plan Básico IA	29.90	PEN	100	mensual	t
bbeebc99-9c0b-4ef8-bb6d-6bb9bd380c02	Plan Pro IA	59.90	PEN	300	mensual	t
\.


--
-- TOC entry 5449 (class 0 OID 37673)
-- Dependencies: 232
-- Data for Name: pregunta; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.pregunta (id, examen_id, texto_pregunta, explicacion_respuesta) FROM stdin;
49f20167-5b5c-5242-a569-41155f546cc1	8828bc78-50e4-5835-b6c9-9290ffd7a97f	¿Qué dispositivo comunica distintas redes IP?	El router consulta rutas IP para reenviar paquetes entre redes.
e580f68b-dc71-5c6c-984b-64ac75d74888	8828bc78-50e4-5835-b6c9-9290ffd7a97f	Si se pierde Internet, ¿puede seguir funcionando una impresora local?	La comunicación dentro de la LAN no exige necesariamente conexión a Internet.
1c239dd2-06a9-539c-a762-c8af19afc738	8828bc78-50e4-5835-b6c9-9290ffd7a97f	¿Qué hace un switch Ethernet de capa 2?	Aprende MAC de origen y consulta la MAC de destino para reenviar tramas.
a2d6a192-66d7-54c9-b3ac-210662915daf	8828bc78-50e4-5835-b6c9-9290ffd7a97f	Si no hay enlace por cable, ¿qué revisarías primero?	Empieza por la conectividad física antes de investigar las capas superiores.
b34913b2-f0d7-55df-b098-a58a4dec4e7c	8828bc78-50e4-5835-b6c9-9290ffd7a97f	¿Cuál es el orden OSI desde la capa inferior?	El modelo OSI organiza las funciones desde señales físicas hasta servicios de aplicación.
20527a0b-e7a3-590f-99a1-0a38dc70272a	2349a3e1-429c-5091-8eaf-a38290a9b3c5	¿Cuál es la red de 192.168.50.10/24?	Con /24, el último octeto de la dirección de red vale 0.
c0cb2824-b2a1-5f83-9636-d26dfdcf536d	2349a3e1-429c-5091-8eaf-a38290a9b3c5	¿Qué máscara corresponde a /26?	Un prefijo /26 corresponde a la máscara 255.255.255.192.
c5db8cb0-b49e-50c6-b776-b745e120153d	2349a3e1-429c-5091-8eaf-a38290a9b3c5	¿En qué subred /26 está 192.168.10.150?	Los bloques avanzan de 64 en 64. 150 está entre 128 y 191.
79a2a9aa-40f0-5306-b2fb-d6feb2d02bee	2349a3e1-429c-5091-8eaf-a38290a9b3c5	Llegas a una IP remota, pero no puedes resolver un nombre. ¿Qué investigarías?	Conviene investigar la configuración y el servicio de resolución de nombres.
02559bd2-5036-5af7-96e3-235f5f34312a	2349a3e1-429c-5091-8eaf-a38290a9b3c5	¿Qué hace DHCP?	Puede entregar IP, máscara, puerta de enlace y DNS mediante una concesión.
93443298-6f18-5ede-b554-ea9d776aec07	ba2a9167-bae0-5a9c-bc1f-648a9029810c	¿Qué dirección aprende un switch al recibir una trama?	El aprendizaje se basa en la MAC de origen y el puerto/VLAN de entrada.
b313bac5-d323-55c0-9e2d-2f27f2286b86	ba2a9167-bae0-5a9c-bc1f-648a9029810c	¿Qué ocurre con un unicast de destino desconocido?	El flooding de unicast desconocido está limitado a la VLAN correspondiente.
6deabcf5-8c41-59a3-927d-e850f80946bb	ba2a9167-bae0-5a9c-bc1f-648a9029810c	¿Qué enlace puede transportar varias VLAN?	Un enlace trunk permite transportar múltiples VLAN según su configuración.
2f16b953-ded9-5a09-b959-13690033dafc	ba2a9167-bae0-5a9c-bc1f-648a9029810c	¿Qué necesitan dos VLAN distintas para comunicarse?	La comunicación entre dominios VLAN requiere funciones de capa 3.
6a0283b9-7d8f-53bd-ac32-cde880982337	ba2a9167-bae0-5a9c-bc1f-648a9029810c	¿Crear VLAN sustituye todos los controles de seguridad?	La segmentación ayuda, pero no sustituye autenticación, filtrado ni políticas de seguridad.
\.


--
-- TOC entry 5456 (class 0 OID 37822)
-- Dependencies: 239
-- Data for Name: progreso_leccion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.progreso_leccion (id, usuario_id, leccion_id, completado, fecha_completado, ultimo_segundo_visto, tiempo_activo, cantidad_visitas, intentos_practica, xp_otorgada_aplicada, primera_actividad, ultima_actividad) FROM stdin;
6c9794e2-5983-4f42-b004-aaba61b2326b	b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22	aaeebc99-9c0b-4ef8-bb6d-6bb9bd380b01	t	2026-10-23 08:13:29.682972	\N	\N	0	0	t	2026-10-23 08:13:29.710832	\N
\.


--
-- TOC entry 5444 (class 0 OID 37600)
-- Dependencies: 227
-- Data for Name: recurso_adjunto; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.recurso_adjunto (id, leccion_id, nombre, tipo_archivo, url_archivo) FROM stdin;
\.


--
-- TOC entry 5445 (class 0 OID 37613)
-- Dependencies: 228
-- Data for Name: recurso_externo_cache; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.recurso_externo_cache (id, leccion_id, tipo, fuente, titulo, descripcion, thumbnail, url, metadata, fecha_creacion, expira_en) FROM stdin;
\.


--
-- TOC entry 5452 (class 0 OID 37718)
-- Dependencies: 235
-- Data for Name: respuesta_intento; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.respuesta_intento (id, intento_id, pregunta_id, opcion_id, es_correcta) FROM stdin;
\.


--
-- TOC entry 5457 (class 0 OID 37846)
-- Dependencies: 240
-- Data for Name: seguimiento_leccion; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.seguimiento_leccion (id, usuario_id, leccion_id, tiempo_acumulado, cantidad_visitas, intentos_practica, primera_actividad, ultima_actividad) FROM stdin;
\.


--
-- TOC entry 5467 (class 0 OID 38003)
-- Dependencies: 250
-- Data for Name: suscripcion_ia; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.suscripcion_ia (id, usuario_id, plan_id, estado, fecha_inicio, fecha_fin, renovacion_automatica, creditos_asignados, precio_cobrado, created_at, updated_at) FROM stdin;
\.


--
-- TOC entry 5436 (class 0 OID 37484)
-- Dependencies: 219
-- Data for Name: usuario; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.usuario (id, nombre, apellido, email, password_hash, rol, avatar_url, email_verificado, fecha_registro, ultima_conexion, updated_at, deleted_at, genero, fecha_nacimiento) FROM stdin;
b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22	María	Diaz	usuario-1@example.invalid	CREDENCIAL_DESHABILITADA	USUARIO	/api/avatares/6437dae8-19fe-487b-a543-25b916e6fd23.png	t	2026-10-17 00:00:42.577721	2026-10-25 02:33:04.907339	2026-10-25 02:33:04.908339	\N	\N	\N
ad4b9272-4ef6-418e-935d-3e68475f95b9	akemi	Pelaez	usuario-2@example.invalid	CREDENCIAL_DESHABILITADA	USUARIO	\N	t	2026-10-23 08:18:28.835517	2026-10-23 08:20:33.134004	2026-10-25 02:38:50.602848	\N	\N	\N
a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11	Carlos	Burga	usuario-3@example.invalid	CREDENCIAL_DESHABILITADA	ADMINISTRADOR	\N	t	2026-10-20 00:00:42.577721	2026-10-25 02:40:45.425674	2026-10-25 02:40:45.513547	\N	\N	\N
\.


--
-- TOC entry 5462 (class 0 OID 37938)
-- Dependencies: 245
-- Data for Name: usuario_medalla; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.usuario_medalla (usuario_id, medalla_id, fecha_obtencion) FROM stdin;
\.


--
-- TOC entry 5464 (class 0 OID 37961)
-- Dependencies: 247
-- Data for Name: usuario_xp; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.usuario_xp (usuario_id, xp_total, nivel, racha_dias, ultima_actividad, updated_at) FROM stdin;
a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11	0	1	0	\N	2026-10-23 00:00:42.577721
b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22	20	1	0	\N	2026-10-23 08:13:29.788799
ad4b9272-4ef6-418e-935d-3e68475f95b9	0	1	0	\N	2026-10-23 08:18:28.873953
\.


--
-- TOC entry 5460 (class 0 OID 37908)
-- Dependencies: 243
-- Data for Name: valoracion_curso; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.valoracion_curso (id, curso_id, usuario_id, puntaje, comentario, fecha) FROM stdin;
\.


--
-- TOC entry 5443 (class 0 OID 37585)
-- Dependencies: 226
-- Data for Name: video_streaming; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.video_streaming (id, leccion_id, aws_s3_key, aws_cloudfront_url, calidad_disponible, duracion_segundos) FROM stdin;
\.


--
-- TOC entry 5121 (class 2606 OID 37634)
-- Name: actividad actividad_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.actividad
    ADD CONSTRAINT actividad_pkey PRIMARY KEY (id);


--
-- TOC entry 5143 (class 2606 OID 37749)
-- Name: calificacion calificacion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.calificacion
    ADD CONSTRAINT calificacion_pkey PRIMARY KEY (id);


--
-- TOC entry 5095 (class 2606 OID 37518)
-- Name: categoria categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (id);


--
-- TOC entry 5163 (class 2606 OID 37877)
-- Name: certificado certificado_codigo_verificacion_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificado
    ADD CONSTRAINT certificado_codigo_verificacion_key UNIQUE (codigo_verificacion);


--
-- TOC entry 5165 (class 2606 OID 37875)
-- Name: certificado certificado_inscripcion_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificado
    ADD CONSTRAINT certificado_inscripcion_id_key UNIQUE (inscripcion_id);


--
-- TOC entry 5167 (class 2606 OID 37873)
-- Name: certificado certificado_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificado
    ADD CONSTRAINT certificado_pkey PRIMARY KEY (id);


--
-- TOC entry 5092 (class 2606 OID 37506)
-- Name: codigo_verificacion codigo_verificacion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.codigo_verificacion
    ADD CONSTRAINT codigo_verificacion_pkey PRIMARY KEY (id);


--
-- TOC entry 5169 (class 2606 OID 37891)
-- Name: comentario_leccion comentario_leccion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.comentario_leccion
    ADD CONSTRAINT comentario_leccion_pkey PRIMARY KEY (id);


--
-- TOC entry 5196 (class 2606 OID 38034)
-- Name: consumo_ia consumo_ia_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.consumo_ia
    ADD CONSTRAINT consumo_ia_pkey PRIMARY KEY (id);


--
-- TOC entry 5103 (class 2606 OID 37544)
-- Name: curso_categoria curso_categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.curso_categoria
    ADD CONSTRAINT curso_categoria_pkey PRIMARY KEY (curso_id, categoria_id);


--
-- TOC entry 5097 (class 2606 OID 37530)
-- Name: curso curso_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.curso
    ADD CONSTRAINT curso_pkey PRIMARY KEY (id);


--
-- TOC entry 5099 (class 2606 OID 37532)
-- Name: curso curso_slug_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.curso
    ADD CONSTRAINT curso_slug_key UNIQUE (slug);


--
-- TOC entry 5123 (class 2606 OID 37648)
-- Name: entrega_actividad entrega_actividad_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.entrega_actividad
    ADD CONSTRAINT entrega_actividad_pkey PRIMARY KEY (id);


--
-- TOC entry 5126 (class 2606 OID 37667)
-- Name: examen examen_leccion_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.examen
    ADD CONSTRAINT examen_leccion_id_key UNIQUE (leccion_id);


--
-- TOC entry 5128 (class 2606 OID 37665)
-- Name: examen examen_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.examen
    ADD CONSTRAINT examen_pkey PRIMARY KEY (id);


--
-- TOC entry 5207 (class 2606 OID 38108)
-- Name: ia_analisis_leccion ia_analisis_leccion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_analisis_leccion
    ADD CONSTRAINT ia_analisis_leccion_pkey PRIMARY KEY (id);


--
-- TOC entry 5205 (class 2606 OID 38084)
-- Name: ia_conversacion_contexto ia_conversacion_contexto_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_conversacion_contexto
    ADD CONSTRAINT ia_conversacion_contexto_pkey PRIMARY KEY (id);


--
-- TOC entry 5199 (class 2606 OID 38052)
-- Name: ia_conversacion ia_conversacion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_conversacion
    ADD CONSTRAINT ia_conversacion_pkey PRIMARY KEY (id);


--
-- TOC entry 5202 (class 2606 OID 38067)
-- Name: ia_mensaje ia_mensaje_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_mensaje
    ADD CONSTRAINT ia_mensaje_pkey PRIMARY KEY (id);


--
-- TOC entry 5150 (class 2606 OID 37803)
-- Name: inscripcion inscripcion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_pkey PRIMARY KEY (id);


--
-- TOC entry 5152 (class 2606 OID 37805)
-- Name: inscripcion inscripcion_usuario_id_curso_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_usuario_id_curso_id_key UNIQUE (usuario_id, curso_id);


--
-- TOC entry 5134 (class 2606 OID 37707)
-- Name: intento_examen intento_examen_examen_id_usuario_id_numero_intento_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.intento_examen
    ADD CONSTRAINT intento_examen_examen_id_usuario_id_numero_intento_key UNIQUE (examen_id, usuario_id, numero_intento);


--
-- TOC entry 5136 (class 2606 OID 37705)
-- Name: intento_examen intento_examen_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.intento_examen
    ADD CONSTRAINT intento_examen_pkey PRIMARY KEY (id);


--
-- TOC entry 5109 (class 2606 OID 37579)
-- Name: leccion leccion_modulo_id_orden_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leccion
    ADD CONSTRAINT leccion_modulo_id_orden_key UNIQUE (modulo_id, orden);


--
-- TOC entry 5111 (class 2606 OID 37577)
-- Name: leccion leccion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leccion
    ADD CONSTRAINT leccion_pkey PRIMARY KEY (id);


--
-- TOC entry 5176 (class 2606 OID 37937)
-- Name: medalla medalla_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.medalla
    ADD CONSTRAINT medalla_pkey PRIMARY KEY (id);


--
-- TOC entry 5105 (class 2606 OID 37564)
-- Name: modulo modulo_curso_id_orden_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.modulo
    ADD CONSTRAINT modulo_curso_id_orden_key UNIQUE (curso_id, orden);


--
-- TOC entry 5107 (class 2606 OID 37562)
-- Name: modulo modulo_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.modulo
    ADD CONSTRAINT modulo_pkey PRIMARY KEY (id);


--
-- TOC entry 5187 (class 2606 OID 37986)
-- Name: movimiento_xp movimiento_xp_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.movimiento_xp
    ADD CONSTRAINT movimiento_xp_pkey PRIMARY KEY (id);


--
-- TOC entry 5189 (class 2606 OID 37988)
-- Name: movimiento_xp movimiento_xp_usuario_id_origen_tipo_origen_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.movimiento_xp
    ADD CONSTRAINT movimiento_xp_usuario_id_origen_tipo_origen_id_key UNIQUE (usuario_id, origen_tipo, origen_id);


--
-- TOC entry 5180 (class 2606 OID 37958)
-- Name: nivel_xp nivel_xp_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.nivel_xp
    ADD CONSTRAINT nivel_xp_pkey PRIMARY KEY (nivel);


--
-- TOC entry 5182 (class 2606 OID 37960)
-- Name: nivel_xp nivel_xp_xp_minimo_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.nivel_xp
    ADD CONSTRAINT nivel_xp_xp_minimo_key UNIQUE (xp_minimo);


--
-- TOC entry 5132 (class 2606 OID 37693)
-- Name: opcion_respuesta opcion_respuesta_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.opcion_respuesta
    ADD CONSTRAINT opcion_respuesta_pkey PRIMARY KEY (id);


--
-- TOC entry 5147 (class 2606 OID 37783)
-- Name: pago pago_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT pago_pkey PRIMARY KEY (id);


--
-- TOC entry 5191 (class 2606 OID 38002)
-- Name: plan_ia plan_ia_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.plan_ia
    ADD CONSTRAINT plan_ia_pkey PRIMARY KEY (id);


--
-- TOC entry 5130 (class 2606 OID 37680)
-- Name: pregunta pregunta_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pregunta
    ADD CONSTRAINT pregunta_pkey PRIMARY KEY (id);


--
-- TOC entry 5155 (class 2606 OID 37832)
-- Name: progreso_leccion progreso_leccion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.progreso_leccion
    ADD CONSTRAINT progreso_leccion_pkey PRIMARY KEY (id);


--
-- TOC entry 5157 (class 2606 OID 37834)
-- Name: progreso_leccion progreso_leccion_usuario_id_leccion_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.progreso_leccion
    ADD CONSTRAINT progreso_leccion_usuario_id_leccion_id_key UNIQUE (usuario_id, leccion_id);


--
-- TOC entry 5117 (class 2606 OID 37607)
-- Name: recurso_adjunto recurso_adjunto_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurso_adjunto
    ADD CONSTRAINT recurso_adjunto_pkey PRIMARY KEY (id);


--
-- TOC entry 5119 (class 2606 OID 37621)
-- Name: recurso_externo_cache recurso_externo_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurso_externo_cache
    ADD CONSTRAINT recurso_externo_cache_pkey PRIMARY KEY (id);


--
-- TOC entry 5140 (class 2606 OID 37723)
-- Name: respuesta_intento respuesta_intento_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.respuesta_intento
    ADD CONSTRAINT respuesta_intento_pkey PRIMARY KEY (id);


--
-- TOC entry 5159 (class 2606 OID 37852)
-- Name: seguimiento_leccion seguimiento_leccion_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.seguimiento_leccion
    ADD CONSTRAINT seguimiento_leccion_pkey PRIMARY KEY (id);


--
-- TOC entry 5161 (class 2606 OID 37854)
-- Name: seguimiento_leccion seguimiento_leccion_usuario_id_leccion_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.seguimiento_leccion
    ADD CONSTRAINT seguimiento_leccion_usuario_id_leccion_id_key UNIQUE (usuario_id, leccion_id);


--
-- TOC entry 5194 (class 2606 OID 38011)
-- Name: suscripcion_ia suscripcion_ia_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.suscripcion_ia
    ADD CONSTRAINT suscripcion_ia_pkey PRIMARY KEY (id);


--
-- TOC entry 5088 (class 2606 OID 37496)
-- Name: usuario usuario_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_email_key UNIQUE (email);


--
-- TOC entry 5178 (class 2606 OID 37943)
-- Name: usuario_medalla usuario_medalla_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario_medalla
    ADD CONSTRAINT usuario_medalla_pkey PRIMARY KEY (usuario_id, medalla_id);


--
-- TOC entry 5090 (class 2606 OID 37494)
-- Name: usuario usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);


--
-- TOC entry 5184 (class 2606 OID 37969)
-- Name: usuario_xp usuario_xp_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario_xp
    ADD CONSTRAINT usuario_xp_pkey PRIMARY KEY (usuario_id);


--
-- TOC entry 5172 (class 2606 OID 37919)
-- Name: valoracion_curso valoracion_curso_curso_id_usuario_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.valoracion_curso
    ADD CONSTRAINT valoracion_curso_curso_id_usuario_id_key UNIQUE (curso_id, usuario_id);


--
-- TOC entry 5174 (class 2606 OID 37917)
-- Name: valoracion_curso valoracion_curso_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.valoracion_curso
    ADD CONSTRAINT valoracion_curso_pkey PRIMARY KEY (id);


--
-- TOC entry 5113 (class 2606 OID 37594)
-- Name: video_streaming video_streaming_leccion_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.video_streaming
    ADD CONSTRAINT video_streaming_leccion_id_key UNIQUE (leccion_id);


--
-- TOC entry 5115 (class 2606 OID 37592)
-- Name: video_streaming video_streaming_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.video_streaming
    ADD CONSTRAINT video_streaming_pkey PRIMARY KEY (id);


--
-- TOC entry 5144 (class 1259 OID 37775)
-- Name: idx_calificacion_usuario_curso; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_calificacion_usuario_curso ON public.calificacion USING btree (usuario_id, curso_id);


--
-- TOC entry 5093 (class 1259 OID 37512)
-- Name: idx_codigo_verificacion_usuario; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_codigo_verificacion_usuario ON public.codigo_verificacion USING btree (usuario_id, tipo);


--
-- TOC entry 5170 (class 1259 OID 37907)
-- Name: idx_comentario_leccion; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_comentario_leccion ON public.comentario_leccion USING btree (leccion_id);


--
-- TOC entry 5197 (class 1259 OID 38045)
-- Name: idx_consumo_ia; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_consumo_ia ON public.consumo_ia USING btree (suscripcion_id, fecha);


--
-- TOC entry 5100 (class 1259 OID 37539)
-- Name: idx_curso_estado; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_curso_estado ON public.curso USING btree (estado);


--
-- TOC entry 5101 (class 1259 OID 37538)
-- Name: idx_curso_responsable; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_curso_responsable ON public.curso USING btree (responsable_id);


--
-- TOC entry 5124 (class 1259 OID 37659)
-- Name: idx_entrega_actividad; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_entrega_actividad ON public.entrega_actividad USING btree (actividad_id, usuario_id);


--
-- TOC entry 5208 (class 1259 OID 38119)
-- Name: idx_ia_analisis; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ia_analisis ON public.ia_analisis_leccion USING btree (usuario_id, leccion_id);


--
-- TOC entry 5200 (class 1259 OID 38058)
-- Name: idx_ia_conversacion_usuario; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ia_conversacion_usuario ON public.ia_conversacion USING btree (usuario_id, fecha_creacion);


--
-- TOC entry 5203 (class 1259 OID 38073)
-- Name: idx_ia_mensaje; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_ia_mensaje ON public.ia_mensaje USING btree (conversacion_id, fecha_creacion);


--
-- TOC entry 5148 (class 1259 OID 37821)
-- Name: idx_inscripcion_curso; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_inscripcion_curso ON public.inscripcion USING btree (curso_id);


--
-- TOC entry 5185 (class 1259 OID 37994)
-- Name: idx_movimiento_xp_fecha; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_movimiento_xp_fecha ON public.movimiento_xp USING btree (usuario_id, fecha);


--
-- TOC entry 5145 (class 1259 OID 37794)
-- Name: idx_pago_usuario; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_pago_usuario ON public.pago USING btree (usuario_id);


--
-- TOC entry 5153 (class 1259 OID 37845)
-- Name: idx_progreso_leccion_leccion; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_progreso_leccion_leccion ON public.progreso_leccion USING btree (leccion_id);


--
-- TOC entry 5138 (class 1259 OID 37739)
-- Name: idx_respuesta_intento; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_respuesta_intento ON public.respuesta_intento USING btree (intento_id);


--
-- TOC entry 5192 (class 1259 OID 38022)
-- Name: idx_suscripcion_ia_usuario; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_suscripcion_ia_usuario ON public.suscripcion_ia USING btree (usuario_id, estado);


--
-- TOC entry 5137 (class 1259 OID 39012)
-- Name: ix_intento_usuario_examen; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_intento_usuario_examen ON public.intento_examen USING btree (usuario_id, examen_id);


--
-- TOC entry 5141 (class 1259 OID 38981)
-- Name: uk_respuesta_intento_pregunta; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uk_respuesta_intento_pregunta ON public.respuesta_intento USING btree (intento_id, pregunta_id);


--
-- TOC entry 5086 (class 1259 OID 38980)
-- Name: uk_usuario_email_normalizado; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uk_usuario_email_normalizado ON public.usuario USING btree (lower(btrim((email)::text)));


--
-- TOC entry 5279 (class 2620 OID 38996)
-- Name: calificacion trg_calificacion_curso; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_calificacion_curso BEFORE INSERT OR UPDATE ON public.calificacion FOR EACH ROW EXECUTE FUNCTION public.validar_calificacion_curso();


--
-- TOC entry 5285 (class 2620 OID 38990)
-- Name: certificado trg_certificado_completo; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_certificado_completo BEFORE INSERT OR UPDATE OF inscripcion_id ON public.certificado FOR EACH ROW EXECUTE FUNCTION public.validar_certificado_completo();


--
-- TOC entry 5275 (class 2620 OID 39023)
-- Name: intento_examen trg_cierre_evaluacion; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_cierre_evaluacion BEFORE INSERT OR UPDATE ON public.intento_examen FOR EACH ROW EXECUTE FUNCTION public.validar_cierre_evaluacion();


--
-- TOC entry 5270 (class 2620 OID 39014)
-- Name: examen trg_configuracion_examen; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_configuracion_examen BEFORE INSERT OR UPDATE ON public.examen FOR EACH ROW EXECUTE FUNCTION public.proteger_configuracion_examen();


--
-- TOC entry 5289 (class 2620 OID 38998)
-- Name: consumo_ia trg_consumo_propietario; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_consumo_propietario BEFORE INSERT OR UPDATE ON public.consumo_ia FOR EACH ROW EXECUTE FUNCTION public.validar_consumo_propietario();


--
-- TOC entry 5290 (class 2620 OID 39003)
-- Name: ia_conversacion_contexto trg_contexto_ia; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_contexto_ia BEFORE INSERT OR UPDATE ON public.ia_conversacion_contexto FOR EACH ROW EXECUTE FUNCTION public.validar_contexto_ia();


--
-- TOC entry 5282 (class 2620 OID 39019)
-- Name: progreso_leccion trg_evaluacion_progreso; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_evaluacion_progreso BEFORE INSERT OR UPDATE OF completado ON public.progreso_leccion FOR EACH ROW EXECUTE FUNCTION public.exigir_evaluacion_progreso();


--
-- TOC entry 5286 (class 2620 OID 39021)
-- Name: certificado trg_evaluaciones_certificado; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_evaluaciones_certificado BEFORE INSERT OR UPDATE OF inscripcion_id ON public.certificado FOR EACH ROW EXECUTE FUNCTION public.exigir_evaluaciones_certificado();


--
-- TOC entry 5267 (class 2620 OID 38979)
-- Name: usuario trg_normalizar_email; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_normalizar_email BEFORE INSERT OR UPDATE OF email ON public.usuario FOR EACH ROW EXECUTE FUNCTION public.normalizar_email_usuario();


--
-- TOC entry 5273 (class 2620 OID 39017)
-- Name: opcion_respuesta trg_opciones_publicadas; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_opciones_publicadas BEFORE INSERT OR DELETE OR UPDATE ON public.opcion_respuesta FOR EACH ROW EXECUTE FUNCTION public.proteger_preguntas_publicadas();


--
-- TOC entry 5271 (class 2620 OID 39016)
-- Name: pregunta trg_preguntas_publicadas; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_preguntas_publicadas BEFORE INSERT OR DELETE OR UPDATE ON public.pregunta FOR EACH ROW EXECUTE FUNCTION public.proteger_preguntas_publicadas();


--
-- TOC entry 5287 (class 2620 OID 39027)
-- Name: certificado trg_preservar_datos_certificado; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_preservar_datos_certificado BEFORE INSERT OR UPDATE ON public.certificado FOR EACH ROW EXECUTE FUNCTION public.preservar_datos_certificado();


--
-- TOC entry 5283 (class 2620 OID 38987)
-- Name: progreso_leccion trg_progreso_inscrito; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_progreso_inscrito BEFORE INSERT OR UPDATE ON public.progreso_leccion FOR EACH ROW EXECUTE FUNCTION public.validar_progreso_inscrito();


--
-- TOC entry 5274 (class 2620 OID 39001)
-- Name: opcion_respuesta trg_proteger_opcion; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_proteger_opcion BEFORE UPDATE OF pregunta_id ON public.opcion_respuesta FOR EACH ROW EXECUTE FUNCTION public.proteger_relaciones_evaluacion();


--
-- TOC entry 5272 (class 2620 OID 39000)
-- Name: pregunta trg_proteger_pregunta; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_proteger_pregunta BEFORE UPDATE OF examen_id ON public.pregunta FOR EACH ROW EXECUTE FUNCTION public.proteger_relaciones_evaluacion();


--
-- TOC entry 5288 (class 2620 OID 39005)
-- Name: suscripcion_ia trg_proteger_suscripcion_consumida; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_proteger_suscripcion_consumida BEFORE UPDATE ON public.suscripcion_ia FOR EACH ROW EXECUTE FUNCTION public.proteger_suscripcion_consumida();


--
-- TOC entry 5277 (class 2620 OID 39025)
-- Name: respuesta_intento trg_respuestas_finalizadas; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_respuestas_finalizadas BEFORE INSERT OR DELETE OR UPDATE ON public.respuesta_intento FOR EACH ROW EXECUTE FUNCTION public.proteger_respuestas_finalizadas();


--
-- TOC entry 5284 (class 2620 OID 38988)
-- Name: seguimiento_leccion trg_seguimiento_inscrito; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_seguimiento_inscrito BEFORE INSERT OR UPDATE ON public.seguimiento_leccion FOR EACH ROW EXECUTE FUNCTION public.validar_progreso_inscrito();


--
-- TOC entry 5268 (class 2620 OID 38129)
-- Name: usuario trg_usuario_fecha_nacimiento; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_usuario_fecha_nacimiento BEFORE INSERT OR UPDATE OF fecha_nacimiento ON public.usuario FOR EACH ROW EXECUTE FUNCTION public.validar_fecha_nacimiento_usuario();


--
-- TOC entry 5269 (class 2620 OID 38131)
-- Name: usuario trg_usuario_genero_obligatorio; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_usuario_genero_obligatorio BEFORE INSERT OR UPDATE OF genero ON public.usuario FOR EACH ROW EXECUTE FUNCTION public.exigir_genero_usuario();


--
-- TOC entry 5281 (class 2620 OID 38983)
-- Name: inscripcion trg_validar_inscripcion_negocio; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_validar_inscripcion_negocio BEFORE INSERT OR UPDATE ON public.inscripcion FOR EACH ROW EXECUTE FUNCTION public.validar_inscripcion_negocio();


--
-- TOC entry 5276 (class 2620 OID 38992)
-- Name: intento_examen trg_validar_intento; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_validar_intento BEFORE INSERT OR UPDATE ON public.intento_examen FOR EACH ROW EXECUTE FUNCTION public.validar_intento_examen();


--
-- TOC entry 5280 (class 2620 OID 38985)
-- Name: pago trg_validar_pago_negocio; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_validar_pago_negocio BEFORE INSERT OR UPDATE ON public.pago FOR EACH ROW EXECUTE FUNCTION public.validar_pago_negocio();


--
-- TOC entry 5278 (class 2620 OID 38994)
-- Name: respuesta_intento trg_validar_respuesta; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_validar_respuesta BEFORE INSERT OR UPDATE ON public.respuesta_intento FOR EACH ROW EXECUTE FUNCTION public.validar_respuesta_examen();


--
-- TOC entry 5218 (class 2606 OID 37635)
-- Name: actividad actividad_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.actividad
    ADD CONSTRAINT actividad_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5229 (class 2606 OID 37755)
-- Name: calificacion calificacion_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.calificacion
    ADD CONSTRAINT calificacion_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE RESTRICT;


--
-- TOC entry 5230 (class 2606 OID 37770)
-- Name: calificacion calificacion_entrega_actividad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.calificacion
    ADD CONSTRAINT calificacion_entrega_actividad_id_fkey FOREIGN KEY (entrega_actividad_id) REFERENCES public.entrega_actividad(id) ON DELETE SET NULL;


--
-- TOC entry 5231 (class 2606 OID 37765)
-- Name: calificacion calificacion_intento_examen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.calificacion
    ADD CONSTRAINT calificacion_intento_examen_id_fkey FOREIGN KEY (intento_examen_id) REFERENCES public.intento_examen(id) ON DELETE SET NULL;


--
-- TOC entry 5232 (class 2606 OID 37760)
-- Name: calificacion calificacion_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.calificacion
    ADD CONSTRAINT calificacion_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE SET NULL;


--
-- TOC entry 5233 (class 2606 OID 37750)
-- Name: calificacion calificacion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.calificacion
    ADD CONSTRAINT calificacion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5244 (class 2606 OID 37878)
-- Name: certificado certificado_inscripcion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.certificado
    ADD CONSTRAINT certificado_inscripcion_id_fkey FOREIGN KEY (inscripcion_id) REFERENCES public.inscripcion(id) ON DELETE RESTRICT;


--
-- TOC entry 5209 (class 2606 OID 37507)
-- Name: codigo_verificacion codigo_verificacion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.codigo_verificacion
    ADD CONSTRAINT codigo_verificacion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5245 (class 2606 OID 37902)
-- Name: comentario_leccion comentario_leccion_comentario_padre_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.comentario_leccion
    ADD CONSTRAINT comentario_leccion_comentario_padre_id_fkey FOREIGN KEY (comentario_padre_id) REFERENCES public.comentario_leccion(id) ON DELETE SET NULL;


--
-- TOC entry 5246 (class 2606 OID 37892)
-- Name: comentario_leccion comentario_leccion_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.comentario_leccion
    ADD CONSTRAINT comentario_leccion_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5247 (class 2606 OID 37897)
-- Name: comentario_leccion comentario_leccion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.comentario_leccion
    ADD CONSTRAINT comentario_leccion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5257 (class 2606 OID 38035)
-- Name: consumo_ia consumo_ia_suscripcion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.consumo_ia
    ADD CONSTRAINT consumo_ia_suscripcion_id_fkey FOREIGN KEY (suscripcion_id) REFERENCES public.suscripcion_ia(id) ON DELETE RESTRICT;


--
-- TOC entry 5258 (class 2606 OID 38040)
-- Name: consumo_ia consumo_ia_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.consumo_ia
    ADD CONSTRAINT consumo_ia_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5211 (class 2606 OID 37550)
-- Name: curso_categoria curso_categoria_categoria_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.curso_categoria
    ADD CONSTRAINT curso_categoria_categoria_id_fkey FOREIGN KEY (categoria_id) REFERENCES public.categoria(id) ON DELETE CASCADE;


--
-- TOC entry 5212 (class 2606 OID 37545)
-- Name: curso_categoria curso_categoria_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.curso_categoria
    ADD CONSTRAINT curso_categoria_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE CASCADE;


--
-- TOC entry 5210 (class 2606 OID 37533)
-- Name: curso curso_responsable_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.curso
    ADD CONSTRAINT curso_responsable_id_fkey FOREIGN KEY (responsable_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5219 (class 2606 OID 37649)
-- Name: entrega_actividad entrega_actividad_actividad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.entrega_actividad
    ADD CONSTRAINT entrega_actividad_actividad_id_fkey FOREIGN KEY (actividad_id) REFERENCES public.actividad(id) ON DELETE CASCADE;


--
-- TOC entry 5220 (class 2606 OID 37654)
-- Name: entrega_actividad entrega_actividad_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.entrega_actividad
    ADD CONSTRAINT entrega_actividad_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5221 (class 2606 OID 37668)
-- Name: examen examen_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.examen
    ADD CONSTRAINT examen_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5259 (class 2606 OID 38074)
-- Name: consumo_ia fk_consumo_mensaje; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.consumo_ia
    ADD CONSTRAINT fk_consumo_mensaje FOREIGN KEY (mensaje_id) REFERENCES public.ia_mensaje(id) ON DELETE SET NULL;


--
-- TOC entry 5234 (class 2606 OID 38023)
-- Name: pago fk_pago_suscripcion; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT fk_pago_suscripcion FOREIGN KEY (suscripcion_id) REFERENCES public.suscripcion_ia(id) ON DELETE SET NULL;


--
-- TOC entry 5265 (class 2606 OID 38114)
-- Name: ia_analisis_leccion ia_analisis_leccion_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_analisis_leccion
    ADD CONSTRAINT ia_analisis_leccion_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5266 (class 2606 OID 38109)
-- Name: ia_analisis_leccion ia_analisis_leccion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_analisis_leccion
    ADD CONSTRAINT ia_analisis_leccion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5262 (class 2606 OID 38085)
-- Name: ia_conversacion_contexto ia_conversacion_contexto_conversacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_conversacion_contexto
    ADD CONSTRAINT ia_conversacion_contexto_conversacion_id_fkey FOREIGN KEY (conversacion_id) REFERENCES public.ia_conversacion(id) ON DELETE CASCADE;


--
-- TOC entry 5263 (class 2606 OID 38090)
-- Name: ia_conversacion_contexto ia_conversacion_contexto_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_conversacion_contexto
    ADD CONSTRAINT ia_conversacion_contexto_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE CASCADE;


--
-- TOC entry 5264 (class 2606 OID 38095)
-- Name: ia_conversacion_contexto ia_conversacion_contexto_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_conversacion_contexto
    ADD CONSTRAINT ia_conversacion_contexto_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5260 (class 2606 OID 38053)
-- Name: ia_conversacion ia_conversacion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_conversacion
    ADD CONSTRAINT ia_conversacion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5261 (class 2606 OID 38068)
-- Name: ia_mensaje ia_mensaje_conversacion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.ia_mensaje
    ADD CONSTRAINT ia_mensaje_conversacion_id_fkey FOREIGN KEY (conversacion_id) REFERENCES public.ia_conversacion(id) ON DELETE CASCADE;


--
-- TOC entry 5237 (class 2606 OID 37811)
-- Name: inscripcion inscripcion_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE RESTRICT;


--
-- TOC entry 5238 (class 2606 OID 37816)
-- Name: inscripcion inscripcion_pago_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_pago_id_fkey FOREIGN KEY (pago_id) REFERENCES public.pago(id) ON DELETE RESTRICT;


--
-- TOC entry 5239 (class 2606 OID 37806)
-- Name: inscripcion inscripcion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5224 (class 2606 OID 37708)
-- Name: intento_examen intento_examen_examen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.intento_examen
    ADD CONSTRAINT intento_examen_examen_id_fkey FOREIGN KEY (examen_id) REFERENCES public.examen(id) ON DELETE RESTRICT;


--
-- TOC entry 5225 (class 2606 OID 37713)
-- Name: intento_examen intento_examen_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.intento_examen
    ADD CONSTRAINT intento_examen_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5214 (class 2606 OID 37580)
-- Name: leccion leccion_modulo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leccion
    ADD CONSTRAINT leccion_modulo_id_fkey FOREIGN KEY (modulo_id) REFERENCES public.modulo(id) ON DELETE CASCADE;


--
-- TOC entry 5213 (class 2606 OID 37565)
-- Name: modulo modulo_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.modulo
    ADD CONSTRAINT modulo_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE CASCADE;


--
-- TOC entry 5254 (class 2606 OID 37989)
-- Name: movimiento_xp movimiento_xp_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.movimiento_xp
    ADD CONSTRAINT movimiento_xp_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5223 (class 2606 OID 37694)
-- Name: opcion_respuesta opcion_respuesta_pregunta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.opcion_respuesta
    ADD CONSTRAINT opcion_respuesta_pregunta_id_fkey FOREIGN KEY (pregunta_id) REFERENCES public.pregunta(id) ON DELETE CASCADE;


--
-- TOC entry 5235 (class 2606 OID 37789)
-- Name: pago pago_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT pago_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE RESTRICT;


--
-- TOC entry 5236 (class 2606 OID 37784)
-- Name: pago pago_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pago
    ADD CONSTRAINT pago_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5222 (class 2606 OID 37681)
-- Name: pregunta pregunta_examen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.pregunta
    ADD CONSTRAINT pregunta_examen_id_fkey FOREIGN KEY (examen_id) REFERENCES public.examen(id) ON DELETE CASCADE;


--
-- TOC entry 5240 (class 2606 OID 37840)
-- Name: progreso_leccion progreso_leccion_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.progreso_leccion
    ADD CONSTRAINT progreso_leccion_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5241 (class 2606 OID 37835)
-- Name: progreso_leccion progreso_leccion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.progreso_leccion
    ADD CONSTRAINT progreso_leccion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5216 (class 2606 OID 37608)
-- Name: recurso_adjunto recurso_adjunto_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurso_adjunto
    ADD CONSTRAINT recurso_adjunto_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5217 (class 2606 OID 37622)
-- Name: recurso_externo_cache recurso_externo_cache_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.recurso_externo_cache
    ADD CONSTRAINT recurso_externo_cache_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5226 (class 2606 OID 37724)
-- Name: respuesta_intento respuesta_intento_intento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.respuesta_intento
    ADD CONSTRAINT respuesta_intento_intento_id_fkey FOREIGN KEY (intento_id) REFERENCES public.intento_examen(id) ON DELETE CASCADE;


--
-- TOC entry 5227 (class 2606 OID 37734)
-- Name: respuesta_intento respuesta_intento_opcion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.respuesta_intento
    ADD CONSTRAINT respuesta_intento_opcion_id_fkey FOREIGN KEY (opcion_id) REFERENCES public.opcion_respuesta(id) ON DELETE SET NULL;


--
-- TOC entry 5228 (class 2606 OID 37729)
-- Name: respuesta_intento respuesta_intento_pregunta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.respuesta_intento
    ADD CONSTRAINT respuesta_intento_pregunta_id_fkey FOREIGN KEY (pregunta_id) REFERENCES public.pregunta(id) ON DELETE RESTRICT;


--
-- TOC entry 5242 (class 2606 OID 37860)
-- Name: seguimiento_leccion seguimiento_leccion_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.seguimiento_leccion
    ADD CONSTRAINT seguimiento_leccion_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


--
-- TOC entry 5243 (class 2606 OID 37855)
-- Name: seguimiento_leccion seguimiento_leccion_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.seguimiento_leccion
    ADD CONSTRAINT seguimiento_leccion_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5255 (class 2606 OID 38017)
-- Name: suscripcion_ia suscripcion_ia_plan_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.suscripcion_ia
    ADD CONSTRAINT suscripcion_ia_plan_id_fkey FOREIGN KEY (plan_id) REFERENCES public.plan_ia(id) ON DELETE RESTRICT;


--
-- TOC entry 5256 (class 2606 OID 38012)
-- Name: suscripcion_ia suscripcion_ia_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.suscripcion_ia
    ADD CONSTRAINT suscripcion_ia_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE RESTRICT;


--
-- TOC entry 5250 (class 2606 OID 37949)
-- Name: usuario_medalla usuario_medalla_medalla_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario_medalla
    ADD CONSTRAINT usuario_medalla_medalla_id_fkey FOREIGN KEY (medalla_id) REFERENCES public.medalla(id) ON DELETE CASCADE;


--
-- TOC entry 5251 (class 2606 OID 37944)
-- Name: usuario_medalla usuario_medalla_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario_medalla
    ADD CONSTRAINT usuario_medalla_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5252 (class 2606 OID 37975)
-- Name: usuario_xp usuario_xp_nivel_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario_xp
    ADD CONSTRAINT usuario_xp_nivel_fkey FOREIGN KEY (nivel) REFERENCES public.nivel_xp(nivel);


--
-- TOC entry 5253 (class 2606 OID 37970)
-- Name: usuario_xp usuario_xp_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.usuario_xp
    ADD CONSTRAINT usuario_xp_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5248 (class 2606 OID 37920)
-- Name: valoracion_curso valoracion_curso_curso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.valoracion_curso
    ADD CONSTRAINT valoracion_curso_curso_id_fkey FOREIGN KEY (curso_id) REFERENCES public.curso(id) ON DELETE CASCADE;


--
-- TOC entry 5249 (class 2606 OID 37925)
-- Name: valoracion_curso valoracion_curso_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.valoracion_curso
    ADD CONSTRAINT valoracion_curso_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuario(id) ON DELETE CASCADE;


--
-- TOC entry 5215 (class 2606 OID 37595)
-- Name: video_streaming video_streaming_leccion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.video_streaming
    ADD CONSTRAINT video_streaming_leccion_id_fkey FOREIGN KEY (leccion_id) REFERENCES public.leccion(id) ON DELETE CASCADE;


-- Completed on 2026-10-25 18:04:04

--
-- PostgreSQL database dump complete
--

\unrestrict HBOLt1E1kZbWo8W84V0iSklnbD46QWtBnumMUo9Jz3M1GPNbDdcbEeexiR0bgeh

