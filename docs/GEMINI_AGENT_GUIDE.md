# Gemini Autonomous AI Browser Agent - User Guide

## 🤖 Overview

This enhanced GitHub Actions workflow transforms Gemini AI into a fully autonomous browser agent capable of:

- **Deep Reasoning**: Multi-step planning and logical task decomposition
- **Web Search**: Real-time information gathering via Google Search grounding
- **Autonomous Execution**: End-to-end task completion without step-by-step instructions
- **Browser Control**: Navigate, click, fill forms, download, upload, extract data
- **Document Understanding**: Analyze long documents and research papers
- **Adaptive Intelligence**: Dynamically adjust plans when obstacles occur

## 🚀 Key Enhancements from Original Workflow

### 1. **Autonomous Agent Architecture**
- **Self-planning**: AI creates its own multi-step execution plan
- **Proactive decision-making**: No waiting for user instructions
- **Dynamic adaptation**: Adjusts strategy when errors occur

### 2. **Web Search Grounding** (NEW)
- Enabled via Google Search integration
- Provides real-time information beyond training data
- Returns citations and sources
- Perfect for research tasks

### 3. **Model Options**
- `gemini-2.0-flash-exp`: Fast, multimodal, production-ready
- `gemini-2.0-flash-thinking-exp`: Shows reasoning process, best for complex tasks
- Configurable via workflow inputs

### 4. **Enhanced Prompting**
- Comprehensive system prompt for autonomous behavior
- Task-specific reasoning and planning
- Context-aware decision making

### 5. **Improved Logging and Monitoring**
- Detailed action logs with timestamps
- Step-by-step execution tracking
- Final summary reports with structured findings

## 📋 How to Use

### Setup Requirements

1. **GitHub Secrets** (required):
   - `GEMINI_API_KEY`: Your Google AI API key ([Get it here](https://aistudio.google.com/app/apikey))
   - `NGROK_AUTHTOKEN`: For remote monitoring ([Get it here](https://dashboard.ngrok.com/get-started/your-authtoken))

2. **Gemini API Tier**:
   - Web search grounding requires **paid tier**
   - Free tier: Limited rate limits, no web search
   - Upgrade at [Google AI Studio](https://aistudio.google.com)

### Running the Workflow

1. Go to your repository on GitHub
2. Click **Actions** tab
3. Select **"Gemini Autonomous AI Browser Agent"**
4. Click **"Run workflow"**
5. Configure inputs:

#### Input Parameters

| Parameter | Description | Default | Example |
|-----------|-------------|---------|---------|
| **task_description** | High-level task for AI | Research AI developments | "Book train ticket Delhi to Mumbai next Thursday" |
| **headless** | Run browser in headless mode | false | true/false |
| **timeout_minutes** | Maximum runtime | 45 | 30, 60 |
| **enable_web_search** | Enable Google Search | true | true/false |
| **model_variant** | Gemini model to use | gemini-2.0-flash-thinking-exp | gemini-2.0-flash-exp |

### Example Tasks

#### 1. Research Task
```
Task: "Research the latest developments in quantum computing and create a summary with sources"
Model: gemini-2.0-flash-thinking-exp
Web Search: true
```

#### 2. Data Collection
```
Task: "Find and extract the top 10 trending repositories on GitHub today"
Model: gemini-2.0-flash-exp
Web Search: true
```

#### 3. Form Automation
```
Task: "Navigate to example.com, fill the contact form with test data, and take screenshots"
Model: gemini-2.0-flash-exp
Web Search: false
```

#### 4. Comparative Analysis
```
Task: "Compare pricing plans of top 3 cloud providers and create a comparison table"
Model: gemini-2.0-flash-thinking-exp
Web Search: true
```

## 🧠 How It Works

### Execution Flow

```
User Request
    ↓
1. AI Analyzes Task
    ↓
2. Creates Multi-Step Plan
    ↓
3. Web Search (if needed)
    ↓
4. For Each Step:
   - Get page context
   - AI decides actions
   - Execute browser actions
   - Take screenshots
   - Log progress
    ↓
5. Generate Summary Report
    ↓
6. Upload Artifacts
```

### AI Decision Making

The agent uses a sophisticated decision-making process:

1. **Planning Phase**:
   - Analyzes task requirements
   - Identifies information gaps
   - Creates step-by-step plan
   - Anticipates potential issues

2. **Execution Phase**:
   - Gathers page context (buttons, forms, text)
   - Decides next actions based on current state
   - Executes browser commands
   - Monitors results

3. **Adaptation Phase**:
   - Detects errors or unexpected layouts
   - Adjusts strategy dynamically
   - Retries with different approach

### Available Browser Actions

```javascript
{
  "goto": { "url": "https://example.com" },
  "click": { "selector": "#button-id" },
  "type": { "selector": "#input-field", "text": "value" },
  "press": { "key": "Enter" },
  "scroll": { "direction": "down", "amount": 500 },
  "wait": { "seconds": 3 },
  "screenshot": { "name": "custom-name" },
  "extract_text": { "selector": ".content" },
  "evaluate": { "code": "document.title" }
}
```

## 📊 Monitoring and Results

### During Execution

- **ngrok URL**: Remote access to control server
- **Real-time logs**: View in GitHub Actions console
- **Status endpoint**: `/status` - Current progress
- **Logs endpoint**: `/logs` - Full action history

### After Completion

**Artifacts** (automatically uploaded):
- **Screenshots**: Before/after each step
- **Videos**: Full browser session recording
- **Logs**: Detailed execution logs with timestamps

**Summary Report**:
- What was accomplished
- Key findings and results
- Issues encountered
- Final status

## 🔧 Configuration Options

### Model Selection

**gemini-2.0-flash-exp** (Recommended for speed):
- 2× faster than Gemini 1.5 Pro
- 1M token context window
- Best for production tasks

**gemini-2.0-flash-thinking-exp** (Recommended for complexity):
- Shows reasoning process
- Better for complex multi-step tasks
- Ideal for research and analysis

### Web Search

Enable for tasks requiring:
- Recent information
- Real-time data
- External research
- Fact verification

Disable for tasks like:
- Simple navigation
- Form filling with known data
- UI testing

## 🎯 Best Practices

### Writing Effective Task Descriptions

**Good Examples**:
- ✅ "Research top 5 AI tools launched in 2025 and summarize their features"
- ✅ "Find the cheapest flight from New York to London next week"
- ✅ "Extract all product names and prices from example-shop.com"

**Bad Examples**:
- ❌ "Do research" (too vague)
- ❌ "Click button" (too specific, not autonomous)
- ❌ "Help me" (unclear objective)

### Safety Considerations

The agent will:
- ⚠️ Log all actions for review
- ⚠️ Request confirmation for sensitive operations (if needed)
- ⚠️ Avoid destructive actions without clear intent
- ⚠️ Respect website terms of service

**You should**:
- Review logs before running on production sites
- Test with sandbox/test environments first
- Monitor execution via ngrok URL
- Use headless=false for critical tasks (visual verification)

## 🔐 Security

- API keys stored in GitHub Secrets (encrypted)
- Browser runs in isolated GitHub Actions runner
- Stealth mode prevents detection
- No data persistence after workflow ends

## 🐛 Troubleshooting

### Agent doesn't start
- Check GEMINI_API_KEY is set correctly
- Verify API key has sufficient quota
- Check if model name is valid

### Web search not working
- Ensure paid tier is enabled
- Verify `enable_web_search: true`
- Check API quota hasn't been exceeded

### Browser actions failing
- Review screenshots to see actual page state
- Check selector syntax (ID, class, CSS)
- Increase wait times for slow-loading pages
- Use `headless: false` to debug visually

### Task not completing
- Increase `timeout_minutes`
- Simplify task description
- Check logs for specific errors

## 📚 Advanced Usage

### Custom Modifications

You can modify `autonomous-agent.js` to:
- Add custom browser actions
- Implement specialized extraction logic
- Integrate with other APIs
- Add custom validation rules

### Integration with Other Tools

The agent can be integrated with:
- Slack/Discord notifications
- Database storage for results
- Email reports
- Custom webhooks

## 🎓 Examples by Use Case

### 1. Research Assistant
```yaml
task: "Research latest papers on [topic] and create annotated bibliography"
model: gemini-2.0-flash-thinking-exp
web_search: true
```

### 2. Price Monitoring
```yaml
task: "Check prices for [product] on top 3 e-commerce sites and compare"
model: gemini-2.0-flash-exp
web_search: true
```

### 3. Content Extraction
```yaml
task: "Extract all article titles and summaries from news website"
model: gemini-2.0-flash-exp
web_search: false
```

### 4. Automated Testing
```yaml
task: "Test user registration flow on staging site with various inputs"
model: gemini-2.0-flash-exp
web_search: false
```

## 📈 Comparison: Original vs Enhanced

| Feature | Original | Enhanced |
|---------|----------|----------|
| Planning | Manual steps | AI-generated plan |
| Web Search | ❌ | ✅ Google Search |
| Model | Fixed | Configurable |
| Autonomy | Semi-autonomous | Fully autonomous |
| Reasoning | Basic | Deep reasoning |
| Adaptation | Static | Dynamic |
| Context | Limited | Full page analysis |
| Logging | Basic | Comprehensive |

## 🚀 Future Enhancements

Potential additions:
- Multi-page navigation graphs
- Session state persistence
- Parallel task execution
- LangChain integration
- Vision-based UI interaction
- Audio/voice commands

## 📞 Support

For issues or questions:
1. Check GitHub Actions logs
2. Review uploaded artifacts
3. Test with simpler tasks first
4. Verify API quotas and keys

## 🎉 Conclusion

This enhanced workflow unlocks the full potential of Gemini AI as an autonomous browser agent. It combines:
- **Intelligence**: Deep reasoning and planning
- **Knowledge**: Real-time web search
- **Action**: Full browser control
- **Reliability**: Error handling and adaptation

Perfect for research, automation, data collection, and complex web tasks!
