[Setup]
AppName=EasePrintAgent
AppVersion=1.0.0
DefaultDirName={pf}\EasePrintAgent
DefaultGroupName=EasePrintAgent
OutputDir=output
OutputBaseFilename=EasePrintAgent-Setup
Compression=lzma
SolidCompression=yes

[Files]
Source: "C:\ForEaseList\EaseListPrinter\build\print-agent-1.0.0.exe"; DestDir: "{app}"; DestName: "PrintAgent.exe"; Flags: ignoreversion
Source: "C:\ForEaseList\EaseListPrinter\build\print-agent-1.0.0.jar"; DestDir: "{app}"; Flags: ignoreversion
Source: "C:\ForEaseList\EaseListPrinter\build\custom-jre\*"; DestDir: "{app}\custom-jre"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\EasePrintAgent"; Filename: "{app}\PrintAgent.exe"
Name: "{userstartup}\EasePrintAgent"; Filename: "{app}\PrintAgent.exe"; WorkingDir: "{app}"

[Run]
Filename: "{app}\PrintAgent.exe"; Description: "Launch PrintAgent now"; Flags: nowait postinstall skipifsilent
