import { ethers } from 'ethers'

const SHARE_RECORDED_EVENT = 'ShareRecorded(bytes32,address,uint256,string,uint256)'

export function shareRecordedTopic0() {
  return ethers.utils.id(SHARE_RECORDED_EVENT)
}

export function uint256Topic(value) {
  return ethers.utils.hexZeroPad(ethers.BigNumber.from(value).toHexString(), 32)
}

export async function findShareRecordedTxHash({ provider, contractAddress, dataHash, shareId }) {
  if (!provider || !contractAddress || !dataHash || shareId === undefined || shareId === null) return null

  const filter = {
    address: contractAddress,
    fromBlock: 0,
    toBlock: 'latest',
    topics: [
      shareRecordedTopic0(),
      ethers.utils.hexZeroPad(dataHash, 32),
      null,
      uint256Topic(shareId)
    ]
  }

  const logs = await provider.getLogs(filter)
  if (!logs || logs.length === 0) return null

  const last = logs[logs.length - 1]
  return last && last.transactionHash ? last.transactionHash : null
}

