REM "外部信号配置"
INCLUDE "GripVar"
REM "当前工具号检查"
UsrProc CheckTool() AS INTEGER 
DIM Tool[8] AS INTEGER  
Tool[8] = 0
Tool[1] = 1
Tool[2] = 2

ToolNum= ISIG2INT(ToolID1 ,4)
bExitLoop = 0					'进入循环
WHILE NOT (bExitLoop=1)
IF RTL1 = 0 AND RTL2 = 0 AND TD1_1 = 1 AND TD1_2 = 1 AND TD2_1 = 1 AND TD2_2 = 1 AND ToolNum= 0 
	RetProc CheckTool = Tool[8]		'返回工具号0
	bExitLoop = 1				'退出循环
ELSEIF RTL1 = 1 AND RTL2 = 1 AND TD1_1 = 0 AND TD1_2 = 0 AND TD2_1 = 1 AND TD2_2 = 1 AND ToolNum= 1
	RetProc CheckTool = Tool[1]		'返回工具号1
	bExitLoop = 1				'退出循环
ELSEIF RTL1 = 1 AND RTL2 = 1 AND TD1_1 = 1 AND TD1_2 = 1 AND TD2_1 = 2 AND TD2_2 = 2 AND ToolNum= 2
 	RetProc CheckTool = Tool[2]		'返回工具号2
	bExitLoop = 1 				'退出循环
ELSE
	USRERR 7036
	bExitLoop = 0				'持续循环
ENDIF
ENDW
EndProc
REM "工具激活"
UsrProc ActiveModule(tempATG AS INTEGER)
	SWITCH tempATG
	CASE TSG1
	FBUSCON 3,1,5		'子设备激活（3号插槽，设备1，等待5秒）		
	'CHGGUN[1]			'焊枪激活
	BREAK
	CASE TSG2
	FBUSCON 3,2,5
	BREAK
	ENDS
EndProc
REM "工具去激活"
UsrProc DeactiveModule(tempDTG AS INTEGER)
	SWITCH tempDTG
	CASE TSG1
	FBUSREL 3, 1, 5		'子设备去激活（3号插槽，设备1，等待5秒）
	'CHGGUN[0]			'焊枪去激活
	BREAK
	CASE TSG2
	FBUSREL 3, 2, 5
	BREAK
	ENDS
EndProc

REM "锁紧工具切换珠子"
UsrProc CloseToolChgDock() 
*CloseDockSTART
	Reset DO_Unlock
	Set DO_lock
	bExitLoop = 0			'进入循环
	WAITL DI_Lock, 3, *CloseDockERR
	WAITL RTL1,3, *CloseDockERR
	WAITL RTL2, 3, *CloseDockERR
	bExitLoop = 1			'退出循环
*CloseDockERR
	WHILE NOT (bExitLoop=1)
	DELAY 0.2
	USRERR 7036
	GOTO *CloseDockSTART
	ENDW
EndProc
REM "打开工具切换珠子"
UsrProc OpenToolChgDock() 
*OpenDockSTART
	Reset DO_lock
	Set DO_Unlock
	bExitLoop = 0			'进入循环
	WAITL DI_UnLock, 3, *OpenDockERR
	WAITJL RTL1, 3, *OpenDockERR
	WAITJL RTL2, 3, *OpenDockERR
	bExitLoop = 1			'退出循环
*OpenDockERR
	WHILE NOT (bExitLoop=1)
	DELAY 0.2
	USRERR 7036
	GOTO *OpenDockSTART
	ENDW
EndProc
REM "关闭支架防护罩"
UsrProc CloseToolCover(tempCTDTG AS INTEGER)
*ClsCoverSTART
	SWITCH tempCTDTG 
	CASE TSG1
		Reset DO_OpenCoverA
		Set DO_ClsCoverA
		bExitLoop = 0			'进入循环
		WAITJL DI_OpenCoverA, 3, *ClsCoverERR
		WAITL DI_ClsCoverA, 3, *ClsCoverERR
		bExitLoop = 1			'退出循环
	BREAK
	CASE TSG2
		Reset DO_OpenCoverB
		Set DO_ClsCoverB
		bExitLoop = 0			'进入循环
		WAITJL DI_OpenCoverB, 3, *ClsCoverERR
		WAITL DI_ClsCoverB, 3, *ClsCoverERR
		bExitLoop = 1			'退出循环
	BREAK
	ENDS
*ClsCoverERR
	WHILE NOT (bExitLoop=1)
	DELAY 0.2
	USRERR 7036
	GOTO *ClsCoverSTART
	ENDW
EndProc
REM "打开支架防护罩"
UsrProc OpenToolCover(tempOTDTG AS INTEGER)
*OpenCoverSTART
	SWITCH tempOTDTG 
	CASE TSG1
		Reset DO_ClsCoverA
		Set DO_OpenCoverA
		bExitLoop = 0			'进入循环
		WAITJL DI_ClsCoverA, 3, *OpenCoverERR
		WAITL DI_OpenCoverA, 3, *OpenCoverERR
		bExitLoop = 1			'退出循环
	BREAK
	CASE TSG2
		Reset DO_ClsCoverB
		Set DO_OpenCoverB
		bExitLoop = 0			'进入循环
		WAITJL DI_ClsCoverB, 3, *OpenCoverERR
		WAITL DI_OpenCoverB, 3, *OpenCoverERR
		bExitLoop = 1			'退出循环
	BREAK
	ENDS
*OpenCoverERR
	WHILE NOT (bExitLoop=1)
	DELAY 0.2
	USRERR 7036
	GOTO *OpenCoverSTART
	ENDW
EndProc


REM "切换盘锁紧并激活工具"
UsrProc CouplerTool(tempCTG AS INTEGER)
	CALLProc CloseToolChgDock()
	CALLProc ActiveModule(tempCTG)
EndProc
REM "去激活工具并打开切换盘"
UsrProc UnCouplerTool(tempUTG AS INTEGER)
	CALLProc DeactiveModule(tempUTG)
	CALLProc OpenToolChgDock()
EndProc
REM "放工具检查"
UsrProc PutToolPrep(tempPTG AS INTEGER)
*PutPrepSTART
	SWITCH tempPTG 
	CASE TSG1
		bExitLoop = 0			'进入循环
		WAITJL TD1_1, 3, *PutPrepERR
		WAITJL TD1_2, 3, *PutPrepERR
		bExitLoop = 1			'退出循环
	BREAK
	CASE TSG2
		bExitLoop = 0			'进入循环
		WAITJL TD2_1, 3, *PutPrepERR
		WAITJL TD2_2, 3, *PutPrepERR
		bExitLoop = 1			'退出循环
	BREAK
	ENDS
*PutPrepERR
	WHILE NOT (bExitLoop=1)
		DELAY 0.2
		USRERR 7036
		GOTO *PutPrepSTART
	ENDW
EndProc
REM "获取工具检查"
UsrProc GetToolPrep(tempGTG AS INTEGER)
*GetPrepSTART
	SWITCH tempGTG 
	CASE TSG1
		bExitLoop = 0			'进入循环
		WAITL TD1_1, 3, *GetPrepERR
		WAITL TD1_2, 3, *GetPrepERR
		bExitLoop = 1			'退出循环
	BREAK
	CASE TSG2
		bExitLoop = 0			'进入循环
		WAITL TD2_1, 3, *GetPrepERR
		WAITL TD2_2, 3, *GetPrepERR
		bExitLoop = 1			'退出循环
	BREAK
	ENDS
*GetPrepERR
	WHILE NOT (bExitLoop=1)
		DELAY 0.2
		USRERR 7036
		GOTO *GetPrepSTART
	ENDW
EndProc
REM "放下工具后检查当前工具状态"
UsrProc ChkPutToolState(tempCPTG AS INTEGER)
*ChkPutStaSTART
	CallProc ToolNum = CheckTool()
	IF (ToolNum =  0 )
		SWITCH tempCPTG 
		CASE TSG1
			bExitLoop = 0			'进入循环
			WAITL TD1_1, 3, *ChkPutStaERR
			WAITL TD1_2, 3, *ChkPutStaERR
			bExitLoop = 1			'退出循环
		BREAK
		CASE TSG2
			bExitLoop = 0			'进入循环
			WAITL TD2_1, 3, *ChkPutStaERR
			WAITL TD2_2, 3, *ChkPutStaERR
			bExitLoop = 1			'退出循环
		BREAK
		ENDS
	ELSE
		GOTO *ChkPutStaERR
	ENDIF
*ChkPutStaERR
	WHILE NOT (bExitLoop=1)
		DELAY 0.2
		USRERR 7036
		GOTO *ChkPutStaSTART
	ENDW
EndProc
REM "获取工具后检查当前工具状态"
UsrProc ChkGetToolState(tempCGTG AS INTEGER)
*ChkGetStaSTART
	CallProc ToolNum = CheckTool()
	IF (ToolNum =  0 )
		GOTO *ChkGetStaERR
	ELSE
		SWITCH tempCGTG
		CASE TSG1
			bExitLoop = 0			'进入循环
			WAITJL TD1_1, 3, *ChkGetStaERR
			WAITJL TD1_2, 3, *ChkGetStaERR
			bExitLoop = 1			'退出循环
		BREAK
		CASE TSG2
			bExitLoop = 0			'进入循环
			WAITJL TD2_1, 3, *ChkGetStaERR
			WAITJL TD2_2, 3, *ChkGetStaERR
			bExitLoop = 1			'退出循环
		BREAK
		ENDS
	
	ENDIF
*ChkGetStaERR
	WHILE NOT (bExitLoop=1)
		DELAY 0.2
		USRERR 7036
		GOTO *ChkGetStaSTART
	ENDW
EndProc



