package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"sync"
	"syscall"
	"time"

	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/client"
)

type Config struct {
	Endpoint     string
	APIKey       string
	BatchSize    int
	FlushInterval time.Duration
	ProjectID    string
}

type LogEntry struct {
	Timestamp     string                 `json:"timestamp"`
	Level         string                 `json:"level,omitempty"`
	Message       string                 `json:"message"`
	Logger        string                 `json:"logger,omitempty"`
	TraceID       string                 `json:"traceId,omitempty"`
	ProjectID     string                 `json:"projectId,omitempty"`
	Environment   string                 `json:"environment,omitempty"`
	ContainerID   string                 `json:"containerId,omitempty"`
	ContainerName string                 `json:"containerName,omitempty"`
	ServiceName   string                 `json:"serviceName,omitempty"`
	ExceptionClass string                `json:"exceptionClass,omitempty"`
	StackTrace    string                 `json:"stackTrace,omitempty"`
	Extra         map[string]interface{} `json:"extra,omitempty"`
}

type Agent struct {
	config     Config
	docker     *client.Client
	buffer     []LogEntry
	bufferMu   sync.Mutex
	httpClient *http.Client
	ctx        context.Context
	cancel     context.CancelFunc
}

func NewAgent(config Config) (*Agent, error) {
	docker, err := client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	if err != nil {
		return nil, fmt.Errorf("failed to create Docker client: %w", err)
	}

	ctx, cancel := context.WithCancel(context.Background())

	return &Agent{
		config: config,
		docker: docker,
		buffer: make([]LogEntry, 0, config.BatchSize*2),
		httpClient: &http.Client{
			Timeout: 30 * time.Second,
		},
		ctx:    ctx,
		cancel: cancel,
	}, nil
}

func (a *Agent) Run() error {
	fmt.Printf("LogHealer Agent starting... endpoint=%s\n", a.config.Endpoint)

	go a.flushLoop()

	containers, err := a.docker.ContainerList(a.ctx, container.ListOptions{})
	if err != nil {
		return fmt.Errorf("failed to list containers: %w", err)
	}

	var wg sync.WaitGroup
	for _, c := range containers {
		if a.shouldMonitor(c) {
			wg.Add(1)
			go func(containerID string, containerName string) {
				defer wg.Done()
				a.tailContainer(containerID, containerName)
			}(c.ID, strings.TrimPrefix(c.Names[0], "/"))
		}
	}

	go a.watchNewContainers(&wg)

	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	fmt.Println("Shutting down...")
	a.cancel()
	wg.Wait()
	a.flush()
	return nil
}

func (a *Agent) shouldMonitor(c types.Container) bool {
	name := strings.TrimPrefix(c.Names[0], "/")
	// Skip loghealer-agent itself and infrastructure containers
	skip := []string{"loghealer-agent", "postgres", "redis", "elasticsearch", "caddy"}
	for _, s := range skip {
		if strings.Contains(name, s) {
			return false
		}
	}
	return true
}

func (a *Agent) watchNewContainers(wg *sync.WaitGroup) {
	events, errs := a.docker.Events(a.ctx, types.EventsOptions{})
	for {
		select {
		case <-a.ctx.Done():
			return
		case err := <-errs:
			if err != nil && err != io.EOF {
				fmt.Printf("Docker events error: %v\n", err)
			}
			return
		case event := <-events:
			if event.Type == "container" && event.Action == "start" {
				containers, _ := a.docker.ContainerList(a.ctx, container.ListOptions{})
				for _, c := range containers {
					if c.ID == event.Actor.ID && a.shouldMonitor(c) {
						wg.Add(1)
						go func(containerID string, containerName string) {
							defer wg.Done()
							a.tailContainer(containerID, containerName)
						}(c.ID, strings.TrimPrefix(c.Names[0], "/"))
					}
				}
			}
		}
	}
}

func (a *Agent) tailContainer(containerID, containerName string) {
	fmt.Printf("Tailing container: %s (%s)\n", containerName, containerID[:12])

	options := container.LogsOptions{
		ShowStdout: true,
		ShowStderr: true,
		Follow:     true,
		Tail:       "100",
		Timestamps: true,
	}

	logs, err := a.docker.ContainerLogs(a.ctx, containerID, options)
	if err != nil {
		fmt.Printf("Failed to tail container %s: %v\n", containerName, err)
		return
	}
	defer logs.Close()

	scanner := bufio.NewScanner(logs)
	for scanner.Scan() {
		select {
		case <-a.ctx.Done():
			return
		default:
			line := scanner.Text()
			// Docker log lines have 8-byte header
			if len(line) > 8 {
				line = line[8:]
			}
			a.processLogLine(line, containerID, containerName)
		}
	}
}

func (a *Agent) processLogLine(line, containerID, containerName string) {
	entry := LogEntry{
		Timestamp:     time.Now().UTC().Format(time.RFC3339Nano),
		ContainerID:   containerID[:12],
		ContainerName: containerName,
		ServiceName:   extractServiceName(containerName),
	}

	// Try to parse as JSON (structured log)
	var jsonLog map[string]interface{}
	if err := json.Unmarshal([]byte(line), &jsonLog); err == nil {
		// Structured JSON log
		if ts, ok := jsonLog["timestamp"].(string); ok {
			entry.Timestamp = ts
		} else if ts, ok := jsonLog["@timestamp"].(string); ok {
			entry.Timestamp = ts
		}
		if level, ok := jsonLog["level"].(string); ok {
			entry.Level = strings.ToUpper(level)
		}
		if msg, ok := jsonLog["message"].(string); ok {
			entry.Message = msg
		}
		if logger, ok := jsonLog["logger"].(string); ok {
			entry.Logger = logger
		}
		if traceID, ok := jsonLog["traceId"].(string); ok {
			entry.TraceID = traceID
		}
		if projectID, ok := jsonLog["projectId"].(string); ok {
			entry.ProjectID = projectID
		}
		if env, ok := jsonLog["environment"].(string); ok {
			entry.Environment = env
		}
		if exc, ok := jsonLog["exceptionClass"].(string); ok {
			entry.ExceptionClass = exc
		}
		if st, ok := jsonLog["stackTrace"].(string); ok {
			entry.StackTrace = st
		}
		entry.Extra = jsonLog
	} else {
		// Plain text log - try to parse Spring Boot format
		entry.Message = line
		entry.Level = extractLogLevel(line)
	}

	if entry.ProjectID == "" {
		entry.ProjectID = a.config.ProjectID
	}

	a.addToBuffer(entry)
}

func extractServiceName(containerName string) string {
	// loghealer-backend-1 -> backend
	parts := strings.Split(containerName, "-")
	if len(parts) >= 2 {
		return parts[len(parts)-2]
	}
	return containerName
}

func extractLogLevel(line string) string {
	line = strings.ToUpper(line)
	levels := []string{"ERROR", "WARN", "INFO", "DEBUG", "TRACE"}
	for _, level := range levels {
		if strings.Contains(line, level) {
			return level
		}
	}
	return "INFO"
}

func (a *Agent) addToBuffer(entry LogEntry) {
	a.bufferMu.Lock()
	defer a.bufferMu.Unlock()

	a.buffer = append(a.buffer, entry)

	if len(a.buffer) >= a.config.BatchSize {
		go a.flush()
	}
}

func (a *Agent) flushLoop() {
	ticker := time.NewTicker(a.config.FlushInterval)
	defer ticker.Stop()

	for {
		select {
		case <-a.ctx.Done():
			return
		case <-ticker.C:
			a.flush()
		}
	}
}

func (a *Agent) flush() {
	a.bufferMu.Lock()
	if len(a.buffer) == 0 {
		a.bufferMu.Unlock()
		return
	}
	batch := make([]LogEntry, len(a.buffer))
	copy(batch, a.buffer)
	a.buffer = a.buffer[:0]
	a.bufferMu.Unlock()

	payload := map[string]interface{}{
		"logs": batch,
	}

	jsonData, err := json.Marshal(payload)
	if err != nil {
		fmt.Printf("Failed to marshal logs: %v\n", err)
		return
	}

	req, err := http.NewRequest("POST", a.config.Endpoint+"/logs/batch", bytes.NewReader(jsonData))
	if err != nil {
		fmt.Printf("Failed to create request: %v\n", err)
		return
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", a.config.APIKey)

	resp, err := a.httpClient.Do(req)
	if err != nil {
		fmt.Printf("Failed to send logs: %v\n", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 400 {
		body, _ := io.ReadAll(resp.Body)
		fmt.Printf("LogHealer API error: %d - %s\n", resp.StatusCode, string(body))
	} else {
		fmt.Printf("Sent %d logs to LogHealer\n", len(batch))
	}
}

func main() {
	config := Config{
		Endpoint:      getEnv("LOGHEALER_ENDPOINT", "https://loghealer.reddia-x.com/api/v1"),
		APIKey:        getEnv("LOGHEALER_API_KEY", ""),
		BatchSize:     50,
		FlushInterval: 5 * time.Second,
		ProjectID:     getEnv("LOGHEALER_PROJECT_ID", ""),
	}

	if config.APIKey == "" {
		fmt.Println("ERROR: LOGHEALER_API_KEY environment variable is required")
		os.Exit(1)
	}

	agent, err := NewAgent(config)
	if err != nil {
		fmt.Printf("Failed to create agent: %v\n", err)
		os.Exit(1)
	}

	if err := agent.Run(); err != nil {
		fmt.Printf("Agent error: %v\n", err)
		os.Exit(1)
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
