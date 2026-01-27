```yaml
  check_yaml_consistency:
    name: 'Check YAML consistency'
    runs-on: 'ubuntu-latest'
    if: '${{ github.event_name != ''workflow_call'' }}'
    steps:
    - id: 'step-0'
      name: 'Check out'
      uses: 'actions/checkout@v4'
    - id: 'step-1'
      name: 'Execute script'
      run: 'rm ''.github/workflows/create-develop-branch.yaml'' && ''.github/workflows/create-develop-branch.main.kts'''
    - id: 'step-2'
      name: 'Consistency check'
      run: 'git diff --exit-code ''.github/workflows/create-develop-branch.yaml'''
```