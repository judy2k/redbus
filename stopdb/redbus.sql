SET default_tablespace = '';
SET default_with_oids = false;

CREATE SEQUENCE services_service_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;
SELECT pg_catalog.setval('services_service_id_seq', 1, true);

CREATE TABLE services (
    service_id integer DEFAULT nextval('services_service_id_seq'::regclass) NOT NULL,
    service_name character varying(20) NOT NULL,
    service_route character varying(255) NOT NULL,
    service_provider character varying(20) NOT NULL,
    created_date timestamp with time zone NOT NULL
);

ALTER TABLE ONLY services
    ADD CONSTRAINT services_pkey PRIMARY KEY (service_id);




CREATE SEQUENCE stops_stop_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;
SELECT pg_catalog.setval('stops_stop_id_seq', 1, false);

CREATE TABLE stops (
    stop_id integer DEFAULT nextval('stops_stop_id_seq'::regclass) NOT NULL,
    stop_code integer NOT NULL,
    stop_name character varying(255) NOT NULL,
    x numeric(20,17) NOT NULL,
    y numeric(20,17) NOT NULL,
    facing character varying(2) NULL,
    stop_type character varying(255) NOT NULL,
    source character varying(20) NOT NULL,
    created_date timestamp with time zone NOT NULL
);

ALTER TABLE ONLY stops
    ADD CONSTRAINT stops_pkey PRIMARY KEY (stop_id);




CREATE SEQUENCE stops_services_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MAXVALUE
    NO MINVALUE
    CACHE 1;
SELECT pg_catalog.setval('stops_services_id_seq', 1, false);

CREATE TABLE stops_services (
    stops_services_id integer DEFAULT nextval('stops_services_id_seq'::regclass) NOT NULL,
    stop_id integer NOT NULL,
    service_id integer NOT NULL,
    created_date timestamp with time zone NOT NULL
);

ALTER TABLE ONLY stops_services
    ADD CONSTRAINT stops_services_pkey PRIMARY KEY (stops_services_id);

ALTER TABLE stops_services
  ADD CONSTRAINT stops_services_service_id_fk FOREIGN KEY (service_id)
      REFERENCES services (service_id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION;

ALTER TABLE stops_services
  ADD CONSTRAINT stops_services_stop_id_fk FOREIGN KEY (stop_id)
      REFERENCES stops (stop_id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION;
