[Setup]
AppName=ChatApp
AppVersion=1.0
DefaultDirName={pf}\ChatApp
DefaultGroupName=ChatApp
OutputDir=installer
OutputBaseFilename=ChatAppInstaller
Compression=lzma
SolidCompression=yes
WizardStyle=modern

[Tasks]
Name: desktopicon; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"

[Files]
Source: "target\ChatApp\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs

[Icons]
Name: "{group}\ChatApp"; Filename: "{app}\bin\app.exe"
Name: "{autodesktop}\ChatApp"; Filename: "{app}\bin\app.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\bin\ChatApp.exe"; Description: "Launch ChatApp"; Flags: nowait postinstall skipifsilent