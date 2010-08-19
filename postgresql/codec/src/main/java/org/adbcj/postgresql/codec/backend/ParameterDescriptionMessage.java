package org.adbcj.postgresql.codec.backend;

public class ParameterDescriptionMessage extends AbstractBackendMessage{

	private final int paramCount;
	private final int[] paramOID;
	
	public ParameterDescriptionMessage(int paramCount, int paramOID[]) {
		this.paramCount = paramCount;
		this.paramOID = paramOID;
	}
	
	@Override
	public BackendMessageType getType() {
		return BackendMessageType.PARAMETER_DESCRIPTION;
	}

	public int getParamCount(){
		return this.paramCount;
	}
	
	public int[] getParamOID(){
		return this.paramOID;
	}
}
