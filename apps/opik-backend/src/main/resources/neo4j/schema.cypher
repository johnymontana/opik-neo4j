// Neo4j Schema Initialization Script
// This script creates constraints and indexes for the Opik graph database
// Run this script once during initial setup or after database reset

// ======================
// STATE LAYER CONSTRAINTS
// ======================

// User constraints
CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE;
CREATE CONSTRAINT user_email_unique IF NOT EXISTS FOR (u:User) REQUIRE u.email IS UNIQUE;

// ApiKey constraints
CREATE CONSTRAINT api_key_id_unique IF NOT EXISTS FOR (a:ApiKey) REQUIRE a.id IS UNIQUE;
CREATE CONSTRAINT api_key_key_unique IF NOT EXISTS FOR (a:ApiKey) REQUIRE a.apiKey IS UNIQUE;

// Workspace constraints
CREATE CONSTRAINT workspace_id_unique IF NOT EXISTS FOR (w:Workspace) REQUIRE w.id IS UNIQUE;
CREATE CONSTRAINT workspace_name_unique IF NOT EXISTS FOR (w:Workspace) REQUIRE w.name IS UNIQUE;

// Project constraints
CREATE CONSTRAINT project_id_unique IF NOT EXISTS FOR (p:Project) REQUIRE p.id IS UNIQUE;

// FeedbackDefinition constraints
CREATE CONSTRAINT feedback_def_id_unique IF NOT EXISTS FOR (fd:FeedbackDefinition) REQUIRE fd.id IS UNIQUE;

// AutomationRule constraints
CREATE CONSTRAINT automation_rule_id_unique IF NOT EXISTS FOR (ar:AutomationRule) REQUIRE ar.id IS UNIQUE;

// Prompt constraints
CREATE CONSTRAINT prompt_id_unique IF NOT EXISTS FOR (pr:Prompt) REQUIRE pr.id IS UNIQUE;

// PromptVersion constraints
CREATE CONSTRAINT prompt_version_id_unique IF NOT EXISTS FOR (pv:PromptVersion) REQUIRE pv.id IS UNIQUE;

// Dataset constraints
CREATE CONSTRAINT dataset_id_unique IF NOT EXISTS FOR (d:Dataset) REQUIRE d.id IS UNIQUE;

// DatasetVersion constraints
CREATE CONSTRAINT dataset_version_id_unique IF NOT EXISTS FOR (dv:DatasetVersion) REQUIRE dv.id IS UNIQUE;

// DatasetItem constraints
CREATE CONSTRAINT dataset_item_id_unique IF NOT EXISTS FOR (di:DatasetItem) REQUIRE di.id IS UNIQUE;

// Experiment constraints
CREATE CONSTRAINT experiment_id_unique IF NOT EXISTS FOR (e:Experiment) REQUIRE e.id IS UNIQUE;

// ExperimentItem constraints
CREATE CONSTRAINT experiment_item_id_unique IF NOT EXISTS FOR (ei:ExperimentItem) REQUIRE ei.id IS UNIQUE;

// Dashboard constraints
CREATE CONSTRAINT dashboard_id_unique IF NOT EXISTS FOR (db:Dashboard) REQUIRE db.id IS UNIQUE;

// Alert constraints
CREATE CONSTRAINT alert_id_unique IF NOT EXISTS FOR (al:Alert) REQUIRE al.id IS UNIQUE;

// Webhook constraints
CREATE CONSTRAINT webhook_id_unique IF NOT EXISTS FOR (wh:Webhook) REQUIRE wh.id IS UNIQUE;

// ======================
// ANALYTICS LAYER CONSTRAINTS
// ======================

// Trace constraints
CREATE CONSTRAINT trace_id_unique IF NOT EXISTS FOR (t:Trace) REQUIRE t.id IS UNIQUE;

// Span constraints
CREATE CONSTRAINT span_id_unique IF NOT EXISTS FOR (s:Span) REQUIRE s.id IS UNIQUE;

// TraceThread constraints
CREATE CONSTRAINT trace_thread_id_unique IF NOT EXISTS FOR (tt:TraceThread) REQUIRE tt.id IS UNIQUE;

// FeedbackScore constraints
CREATE CONSTRAINT feedback_score_id_unique IF NOT EXISTS FOR (fs:FeedbackScore) REQUIRE fs.id IS UNIQUE;

// Comment constraints
CREATE CONSTRAINT comment_id_unique IF NOT EXISTS FOR (c:Comment) REQUIRE c.id IS UNIQUE;

// Attachment constraints
CREATE CONSTRAINT attachment_id_unique IF NOT EXISTS FOR (at:Attachment) REQUIRE at.id IS UNIQUE;

// Guardrail constraints
CREATE CONSTRAINT guardrail_id_unique IF NOT EXISTS FOR (g:Guardrail) REQUIRE g.id IS UNIQUE;

// Optimization constraints
CREATE CONSTRAINT optimization_id_unique IF NOT EXISTS FOR (o:Optimization) REQUIRE o.id IS UNIQUE;

// ======================
// PERFORMANCE INDEXES
// ======================

// Trace indexes for filtering and sorting
CREATE INDEX trace_start_time IF NOT EXISTS FOR (t:Trace) ON (t.startTime);
CREATE INDEX trace_end_time IF NOT EXISTS FOR (t:Trace) ON (t.endTime);
CREATE INDEX trace_workspace_id IF NOT EXISTS FOR (t:Trace) ON (t.workspaceId);
CREATE INDEX trace_project_id IF NOT EXISTS FOR (t:Trace) ON (t.projectId);
CREATE INDEX trace_thread_id IF NOT EXISTS FOR (t:Trace) ON (t.threadId);
CREATE INDEX trace_name IF NOT EXISTS FOR (t:Trace) ON (t.name);
CREATE INDEX trace_created_by IF NOT EXISTS FOR (t:Trace) ON (t.createdBy);
CREATE INDEX trace_last_updated_at IF NOT EXISTS FOR (t:Trace) ON (t.lastUpdatedAt);

// Span indexes for filtering and sorting
CREATE INDEX span_start_time IF NOT EXISTS FOR (s:Span) ON (s.startTime);
CREATE INDEX span_end_time IF NOT EXISTS FOR (s:Span) ON (s.endTime);
CREATE INDEX span_workspace_id IF NOT EXISTS FOR (s:Span) ON (s.workspaceId);
CREATE INDEX span_project_id IF NOT EXISTS FOR (s:Span) ON (s.projectId);
CREATE INDEX span_trace_id IF NOT EXISTS FOR (s:Span) ON (s.traceId);
CREATE INDEX span_parent_span_id IF NOT EXISTS FOR (s:Span) ON (s.parentSpanId);
CREATE INDEX span_name IF NOT EXISTS FOR (s:Span) ON (s.name);
CREATE INDEX span_type IF NOT EXISTS FOR (s:Span) ON (s.type);
CREATE INDEX span_created_by IF NOT EXISTS FOR (s:Span) ON (s.createdBy);
CREATE INDEX span_last_updated_at IF NOT EXISTS FOR (s:Span) ON (s.lastUpdatedAt);

// Project indexes
CREATE INDEX project_workspace_id IF NOT EXISTS FOR (p:Project) ON (p.workspaceId);
CREATE INDEX project_name IF NOT EXISTS FOR (p:Project) ON (p.name);
CREATE INDEX project_created_at IF NOT EXISTS FOR (p:Project) ON (p.createdAt);

// Workspace indexes
CREATE INDEX workspace_created_at IF NOT EXISTS FOR (w:Workspace) ON (w.createdAt);

// TraceThread indexes
CREATE INDEX trace_thread_workspace_id IF NOT EXISTS FOR (tt:TraceThread) ON (tt.workspaceId);
CREATE INDEX trace_thread_project_id IF NOT EXISTS FOR (tt:TraceThread) ON (tt.projectId);

// FeedbackScore indexes
CREATE INDEX feedback_score_created_at IF NOT EXISTS FOR (fs:FeedbackScore) ON (fs.createdAt);
CREATE INDEX feedback_score_name IF NOT EXISTS FOR (fs:FeedbackScore) ON (fs.name);

// Comment indexes
CREATE INDEX comment_created_at IF NOT EXISTS FOR (c:Comment) ON (c.createdAt);
CREATE INDEX comment_created_by IF NOT EXISTS FOR (c:Comment) ON (c.createdBy);

// Dataset indexes
CREATE INDEX dataset_workspace_id IF NOT EXISTS FOR (d:Dataset) ON (d.workspaceId);
CREATE INDEX dataset_name IF NOT EXISTS FOR (d:Dataset) ON (d.name);
CREATE INDEX dataset_created_at IF NOT EXISTS FOR (d:Dataset) ON (d.createdAt);

// Experiment indexes
CREATE INDEX experiment_project_id IF NOT EXISTS FOR (e:Experiment) ON (e.projectId);
CREATE INDEX experiment_dataset_id IF NOT EXISTS FOR (e:Experiment) ON (e.datasetId);
CREATE INDEX experiment_created_at IF NOT EXISTS FOR (e:Experiment) ON (e.createdAt);

// Prompt indexes
CREATE INDEX prompt_workspace_id IF NOT EXISTS FOR (pr:Prompt) ON (pr.workspaceId);
CREATE INDEX prompt_name IF NOT EXISTS FOR (pr:Prompt) ON (pr.name);
CREATE INDEX prompt_created_at IF NOT EXISTS FOR (pr:Prompt) ON (pr.createdAt);

// User indexes
CREATE INDEX user_created_at IF NOT EXISTS FOR (u:User) ON (u.createdAt);
CREATE INDEX user_full_name IF NOT EXISTS FOR (u:User) ON (u.fullName);

// ApiKey indexes
CREATE INDEX api_key_user_id IF NOT EXISTS FOR (a:ApiKey) ON (a.userId);
CREATE INDEX api_key_workspace_id IF NOT EXISTS FOR (a:ApiKey) ON (a.workspaceId);
CREATE INDEX api_key_created_at IF NOT EXISTS FOR (a:ApiKey) ON (a.createdAt);

// ======================
// COMPOSITE INDEXES
// ======================

// Composite index for trace filtering by project and time
CREATE INDEX trace_project_start_time IF NOT EXISTS FOR (t:Trace) ON (t.projectId, t.startTime);

// Composite index for span filtering by trace and time
CREATE INDEX span_trace_start_time IF NOT EXISTS FOR (s:Span) ON (s.traceId, t.startTime);

// Composite index for project filtering by workspace and name
CREATE INDEX project_workspace_name IF NOT EXISTS FOR (p:Project) ON (p.workspaceId, p.name);

// ======================
// FULLTEXT INDEXES
// ======================

// Fulltext search on trace names and content
CREATE FULLTEXT INDEX trace_fulltext IF NOT EXISTS FOR (t:Trace) ON EACH [t.name, t.input, t.output];

// Fulltext search on span names and content
CREATE FULLTEXT INDEX span_fulltext IF NOT EXISTS FOR (s:Span) ON EACH [s.name, s.input, s.output];

// Fulltext search on project names
CREATE FULLTEXT INDEX project_fulltext IF NOT EXISTS FOR (p:Project) ON EACH [p.name, p.description];

// Fulltext search on dataset names
CREATE FULLTEXT INDEX dataset_fulltext IF NOT EXISTS FOR (d:Dataset) ON EACH [d.name, d.description];

// Fulltext search on prompt names and content
CREATE FULLTEXT INDEX prompt_fulltext IF NOT EXISTS FOR (pr:Prompt) ON EACH [pr.name, pr.description];

// ======================
// SCHEMA INFO
// ======================

// Display all constraints
SHOW CONSTRAINTS;

// Display all indexes
SHOW INDEXES;

